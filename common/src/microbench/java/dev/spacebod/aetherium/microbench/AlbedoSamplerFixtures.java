package dev.spacebod.aetherium.microbench;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import dev.spacebod.aetherium.shaders.engine.ShaderPackEngine;
import dev.spacebod.aetherium.shaders.shaderpack.materialmap.WorldRenderingSettings;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/** Alternating terrain/entity filtering must not allocate a new native sampler on every draw. No GPU needed. */
final class AlbedoSamplerFixtures {
	private AlbedoSamplerFixtures() {
	}

	static void run() {
		var settings = WorldRenderingSettings.INSTANCE;
		boolean oldBreaksAnisotropy = settings.breaksAnisotropy();
		List<TestSampler> allocated = new ArrayList<>();
		try {
			Field deviceField = RenderSystem.class.getDeclaredField("DEVICE");
			deviceField.setAccessible(true);
			Object oldDevice = deviceField.get(null);
			GpuDevice device = (GpuDevice) Proxy.newProxyInstance(GpuDevice.class.getClassLoader(), new Class<?>[]{GpuDevice.class},
					(proxy, method, args) -> {
						if (!method.getName().equals("createSampler")) {
							throw new AssertionError("unexpected GPU operation: " + method.getName());
						}
						if (args[2] != FilterMode.NEAREST || args[3] != FilterMode.NEAREST || !args[5].equals(OptionalDouble.empty())) {
							throw new AssertionError("pack albedo filtering changed");
						}
						TestSampler sampler = new TestSampler((AddressMode) args[0], (AddressMode) args[1], (Integer) args[4]);
						allocated.add(sampler);
						return sampler;
					});
			var constructor = ShaderPackEngine.class.getDeclaredConstructor();
			constructor.setAccessible(true);
			ShaderPackEngine engine = constructor.newInstance();
			var teardown = ShaderPackEngine.class.getDeclaredMethod("teardown");
			teardown.setAccessible(true);
			try {
				deviceField.set(null, device);
				settings.setBreaksAnisotropy(false);
				Field state = ShaderPackEngine.class.getDeclaredField("state");
				state.setAccessible(true);
				for (Object value : state.getType().getEnumConstants()) {
					if (value.toString().equals("ACTIVE")) state.set(engine, value);
				}
				for (AddressMode u : AddressMode.values()) {
					for (AddressMode v : AddressMode.values()) {
						TestSampler plain = new TestSampler(u, v, 1);
						TestSampler terrain = new TestSampler(u, v, 16);
						same(terrain, engine.albedoSampler(terrain), "outside a pack pass");
						set(engine, "gbufferPassOpen", true);
						GpuSampler entityFilter = engine.albedoSampler(plain);
						GpuSampler terrainFilter = engine.albedoSampler(terrain);
						for (int draw = 0; draw < 10_000; draw++) {
							same(entityFilter, engine.albedoSampler(plain), "entity filtering after terrain");
							same(terrainFilter, engine.albedoSampler(terrain), "terrain filtering after entities");
							same(terrainFilter, engine.albedoSampler(terrainFilter), "recursive sampler rebind");
						}
						if (terrainFilter.getAddressModeU() != u || terrainFilter.getAddressModeV() != v
								|| terrainFilter.getMaxAnisotropy() != 16 || entityFilter.getMaxAnisotropy() != 1) {
							throw new AssertionError("address mode or anisotropy lost");
						}
						settings.setBreaksAnisotropy(true);
						same(entityFilter, engine.albedoSampler(terrain), "pack disables anisotropy");
						settings.setBreaksAnisotropy(false);
						set(engine, "gbufferPassOpen", false);
						set(engine, "shadowPassOpen", true);
						same(terrainFilter, engine.albedoSampler(terrain), "shadow filtering");
						set(engine, "shadowPassOpen", false);
					}
				}
				int expected = 2 * AddressMode.values().length * AddressMode.values().length;
				if (allocated.size() != expected || allocated.stream().anyMatch(s -> s.closes != 0)) {
					throw new AssertionError("sampler allocations grew with draw count or in-flight samplers were closed");
				}
				set(engine, "gbufferPassOpen", true);
				TestSampler changed = new TestSampler(AddressMode.REPEAT, AddressMode.REPEAT, 8);
				GpuSampler changedFilter = engine.albedoSampler(changed);
				for (int i = 0; i < 10_000; i++) {
					engine.albedoSampler(new TestSampler(AddressMode.REPEAT, AddressMode.REPEAT, 16));
					same(changedFilter, engine.albedoSampler(changed), "anisotropy setting changes");
				}
				if (allocated.size() != expected + 1) throw new AssertionError("settings changes leak samplers");
				teardown.invoke(engine);
				if (allocated.stream().anyMatch(s -> s.closes != 1)) throw new AssertionError("teardown did not close every sampler once");
				if (engine.albedoSampler(changed) == changedFilter) throw new AssertionError("reload reused a closed sampler");
			} finally {
				try {
					teardown.invoke(engine);
				} finally {
					deviceField.set(null, oldDevice);
					settings.setBreaksAnisotropy(oldBreaksAnisotropy);
				}
			}
			if (allocated.stream().anyMatch(s -> s.closes != 1)) throw new AssertionError("reload teardown leaked or double-closed a sampler");
			System.out.println("albedo samplers: alternating draw/anisotropy states stay bounded; shadow/filtering/rebind and reload cleanup pass");
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("albedo sampler regression", e);
		}
	}

	private static void same(GpuSampler expected, GpuSampler actual, String context) {
		if (expected != actual) throw new AssertionError("sampler was recreated: " + context);
	}

	private static void set(ShaderPackEngine engine, String name, Object value) throws ReflectiveOperationException {
		Field field = ShaderPackEngine.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(engine, value);
	}

	private static final class TestSampler implements GpuSampler {
		private final AddressMode u, v;
		private final int anisotropy;
		private int closes;

		TestSampler(AddressMode u, AddressMode v, int anisotropy) {
			this.u = u;
			this.v = v;
			this.anisotropy = anisotropy;
		}

		@Override public AddressMode getAddressModeU() { return u; }
		@Override public AddressMode getAddressModeV() { return v; }
		@Override public FilterMode getMinFilter() { return FilterMode.NEAREST; }
		@Override public FilterMode getMagFilter() { return FilterMode.NEAREST; }
		@Override public int getMaxAnisotropy() { return anisotropy; }
		@Override public OptionalDouble getMaxLod() { return OptionalDouble.empty(); }
		@Override public boolean isClosed() { return closes != 0; }
		@Override public void close() { closes++; }
	}
}
