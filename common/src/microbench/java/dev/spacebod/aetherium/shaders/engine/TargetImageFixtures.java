package dev.spacebod.aetherium.shaders.engine;

import com.mojang.renderpearl.api.GpuFormat;
import dev.spacebod.aetherium.shaders.gl.texture.InternalTextureFormat;
import dev.spacebod.aetherium.shaders.gl.texture.PixelFormat;
import dev.spacebod.aetherium.shaders.gl.texture.PixelType;
import dev.spacebod.aetherium.shaders.shaderpack.texture.CustomTextureData;
import dev.spacebod.aetherium.shaders.shaderpack.texture.TextureFilteringData;
import java.util.List;
import java.util.Map;
import org.lwjgl.vulkan.VK10;

public final class TargetImageFixtures {
	private TargetImageFixtures() {
	}

	public static void main(String[] args) {
		int original = VK10.VK_IMAGE_CREATE_CUBE_COMPATIBLE_BIT;
		TargetStorage.configure(true);
		try {
			if (TargetStorage.imageFlags(original) != original) {
				throw new AssertionError("non-target creation flags changed");
			}
			int target = TargetStorage.create(() -> TargetStorage.imageFlags(original));
			if (target != (original | VK10.VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT)) {
				throw new AssertionError("target creation lost mutable format or existing flags");
			}
			try {
				TargetStorage.create(() -> { throw new IllegalStateException("fixture"); });
			} catch (IllegalStateException expected) {
				if (!expected.getMessage().equals("fixture")) {
					throw expected;
				}
			}
			if (TargetStorage.imageFlags(original) != original) {
				throw new AssertionError("target creation leaked its flag scope");
			}
		} finally {
			TargetStorage.configure(false);
		}
		if (TargetStorage.create(() -> TargetStorage.imageFlags(original)) != original) {
			throw new AssertionError("an inactive target-storage path requested mutable images");
		}
		Map<String, StorageBindings.Image> images = Map.of("grid", new StorageBindings.Image("grid", null, 0, GpuFormat.R32_UINT));
		List<ImageViews.View> aliases = ImageViews.scan(images, List.of("layout(rgba8) uniform image2D grid;"));
		if (aliases.size() != 1 || aliases.getFirst().format() != GpuFormat.RGBA8_UNORM || !aliases.getFirst().storage()) {
			throw new AssertionError("a compatible storage format alias was lost");
		}
		if (!ImageViews.scan(images, List.of("layout(rgba16f) uniform image2D grid;")).isEmpty()) {
			throw new AssertionError("an alias with a different texel size was accepted");
		}
		TextureFilteringData filtering = new TextureFilteringData(false, false);
		var one = new CustomTextureData.RawData1D(new byte[4], filtering, InternalTextureFormat.RGBA8, PixelFormat.RGBA, PixelType.UNSIGNED_BYTE, 1);
		var two = new CustomTextureData.RawData2D(new byte[4], filtering, InternalTextureFormat.RGBA8, PixelFormat.RGBA, PixelType.UNSIGNED_BYTE, 1, 1);
		if (CustomTextures.vanillaCanHold(one) || !CustomTextures.vanillaCanHold(two)) {
			throw new AssertionError("raw texture dimensionality was lost");
		}
		System.out.println("target images: creation flags/scoping, storage alias formats and raw texture dimensionality pass");
	}
}
