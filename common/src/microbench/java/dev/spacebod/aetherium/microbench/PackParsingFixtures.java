package dev.spacebod.aetherium.microbench;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import com.google.common.collect.ImmutableList;
import dev.spacebod.aetherium.client.gpu.StorageFeatures;
import dev.spacebod.aetherium.shaders.features.FeatureFlags;
import dev.spacebod.aetherium.shaders.parsing.ExpressionFunctions;
import dev.spacebod.aetherium.shaders.parsing.ExpressionOperators;
import dev.spacebod.aetherium.shaders.parsing.VectorType;
import dev.spacebod.aetherium.shaders.shaderpack.ShaderPack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kroppeb.stareval.function.FunctionReturn;
import kroppeb.stareval.function.Type;
import kroppeb.stareval.parser.Parser;
import kroppeb.stareval.resolver.ExpressionResolver;
import org.joml.Vector2f;

public final class PackParsingFixtures {
	private PackParsingFixtures() {
	}

	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		materialMaps();
		computeSamplerUsage();
		propertyComments();
		fragmentOutputScan();
		defaultShadowBuffers();
		customExpressions();
		fractionalExpressions();
		imageDeclarationsAndFeatures();
	}

	private static void fractionalExpressions() {
		try {
			ExpressionResolver resolver = new ExpressionResolver(ExpressionFunctions.functions, name -> null, true);
			FunctionReturn value = new FunctionReturn();
			resolver.resolveExpression(Type.Float, Parser.parse("fract(-1.25)", ExpressionOperators.options)).evaluateTo(null, value);
			if (Math.abs(value.floatReturn - 0.75f) > 1e-7f) {
				throw new AssertionError("scalar fractional part did not use floor");
			}
			resolver.resolveExpression(VectorType.VEC2, Parser.parse("fract(vec2(-1.25, 2.5))", ExpressionOperators.options)).evaluateTo(null, value);
			Vector2f vector = (Vector2f) value.objectReturn;
			if (Math.abs(vector.x - 0.75f) > 1e-7f || Math.abs(vector.y - 0.5f) > 1e-7f) {
				throw new AssertionError("vector fractional part lost component-wise evaluation");
			}
		} catch (Exception e) {
			throw new AssertionError("fractional expression could not be resolved", e);
		}
		System.out.println("fractional expressions: scalar and vector negative operands pass");
	}

	private static void imageDeclarationsAndFeatures() {
		StorageFeatures.assumeForCompileCheck();
		try {
			Path directory = Files.createTempDirectory("aetherium-pack-properties-");
			try {
				StringBuilder properties = new StringBuilder("iris.features.required=CUSTOM_IMAGES\n"
						+ "iris.features.optional=TESSELLATION_SHADERS UNKNOWN_FEATURE TEXTURE_FILTERING\n");
				for (int image = 0; image < 33; image++) {
					properties.append("image.test").append(image).append("=none   RGBA\tRGBA8   UNSIGNED_BYTE true false 4 4\n");
				}
				Files.writeString(directory.resolve("shaders.properties"), properties);
				ShaderPack pack = new ShaderPack(directory, ImmutableList.of(), false);
				if (pack.getCustomImages().size() != 32) {
					throw new AssertionError("whitespace-separated image declarations did not stop at 32 images");
				}
				if (pack.hasFeature(FeatureFlags.TESSELLATION_SHADERS) || pack.hasFeature(FeatureFlags.UNKNOWN)
						|| !pack.hasFeature(FeatureFlags.TEXTURE_FILTERING)) {
					throw new AssertionError("optional feature flags did not follow feature availability");
				}
			} finally {
				Files.deleteIfExists(directory.resolve("shaders.properties"));
				Files.delete(directory);
			}
		} catch (IOException e) {
			throw new AssertionError("cannot prepare image/feature parsing fixture", e);
		}
		System.out.println("pack properties: whitespace, 32-image limit and optional feature availability pass");
	}

	private static void materialMaps() {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		var entries = new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<List<dev.spacebod.aetherium.shaders.shaderpack.materialmap.BlockEntry>>();
		var mixedCase = new dev.spacebod.aetherium.shaders.shaderpack.materialmap.NamespacedId("minecraft", "polished_Diorite_slab");
		var invalid = new dev.spacebod.aetherium.shaders.shaderpack.materialmap.NamespacedId("minecraft", "invalid block");
		entries.put(9, List.of(new dev.spacebod.aetherium.shaders.shaderpack.materialmap.BlockEntry(invalid, Map.of()),
				new dev.spacebod.aetherium.shaders.shaderpack.materialmap.BlockEntry(mixedCase, Map.of()),
				new dev.spacebod.aetherium.shaders.shaderpack.materialmap.BlockEntry(
						new dev.spacebod.aetherium.shaders.shaderpack.materialmap.NamespacedId("minecraft", "stone"), Map.of())));
		var ids = dev.spacebod.aetherium.shaders.shaderpack.materialmap.BlockMaterialMapping.createBlockStateIdMap(entries,
				new it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap<>());
		if (ids.getInt(net.minecraft.world.level.block.Blocks.POLISHED_DIORITE_SLAB.defaultBlockState()) != 9
				|| ids.getInt(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()) != 9) {
			throw new AssertionError("a malformed block ID discarded valid entries in the same material group");
		}
		var type = dev.spacebod.aetherium.shaders.shaderpack.materialmap.BlockRenderType.CUTOUT;
		var types = dev.spacebod.aetherium.shaders.shaderpack.materialmap.BlockMaterialMapping.createBlockTypeMap(Map.of(mixedCase, type, invalid, type));
		if (types.size() != 1 || types.get(net.minecraft.world.level.block.Blocks.POLISHED_DIORITE_SLAB) != type) {
			throw new AssertionError("block layer IDs did not use the same tolerant lookup");
		}
		System.out.println("material maps: mixed-case IDs resolved; invalid entries skipped; valid entries retained");
	}

	/** Compute-only readers must keep their depth copies, even without a fragment program reading the same map. */
	private static void computeSamplerUsage() {
		var files = Map.of(
				"setup7.csh", "uniform sampler2D setupOnly;",
				"shadow_a.csh", "uniform sampler2D shadowOnly;",
				"final_a.csh", "uniform sampler2D depthtex2;",
				"begin3_a.csh", "uniform sampler2D beginOnly;",
				"prepare4_a.csh", "uniform sampler2D prepareOnly;",
				"shadowcomp5_a.csh", "uniform sampler2D shadowcompOnly;",
				"deferred6_a.csh", "uniform sampler2D deferredOnly;",
				"composite7_a.csh", "uniform sampler2DShadow shadowtex1HW;");
		try {
			var directory = java.nio.file.Files.createTempDirectory("aetherium-compute-usage-");
			try {
				for (var file : files.entrySet()) {
					java.nio.file.Files.writeString(directory.resolve(file.getKey()),
							"#version 430\nlayout(local_size_x=1) in;\n" + file.getValue() + "\nvoid main() {}\n");
				}
				var pack = new dev.spacebod.aetherium.shaders.shaderpack.ShaderPack(directory,
						com.google.common.collect.ImmutableList.of(), false);
				var usage = dev.spacebod.aetherium.shaders.engine.SamplerUsage.of(pack.getProgramSet(
						dev.spacebod.aetherium.shaders.shaderpack.DimensionId.OVERWORLD));
				for (String name : List.of("setupOnly", "shadowOnly", "depthtex2", "beginOnly", "prepareOnly", "shadowcompOnly", "deferredOnly", "shadowtex1HW")) {
					if (!usage.declares(name)) throw new AssertionError("compute-only sampler was missed: " + name);
				}
				if (!usage.samplesOpaqueShadowDepth() || !usage.samplesDepth(dev.spacebod.aetherium.shaders.engine.DepthTargets.NO_HAND)) {
					throw new AssertionError("compute-only readers lost their opaque shadow or no-hand depth copy");
				}
			} finally {
				for (String file : files.keySet()) java.nio.file.Files.deleteIfExists(directory.resolve(file));
				java.nio.file.Files.delete(directory);
			}
		} catch (java.io.IOException e) {
			throw new AssertionError("cannot prepare compute sampler fixture", e);
		}
		System.out.println("sampler usage: compute-only readers retained across setup, shadow, final and all frame stages");
	}

	private static void propertyComments() {
		String processed = dev.spacebod.aetherium.shaders.shaderpack.preprocessor.PropertiesPreprocessor.preprocessSource(
				"# Non-English comment bytes: \u0085 comment tail\r\nitem.1=minecraft:torch\r# another \u0085 tail\nitem.2=minecraft:lantern\n", List.of());
		if (!processed.contains("item.1=minecraft:torch") || !processed.contains("item.2=minecraft:lantern")
				|| processed.contains("tail")) {
			throw new AssertionError("non-English property comments truncated or contaminated the ID map: " + processed);
		}
		System.out.println("property comments: CR, LF and CRLF retained; embedded Latin-1 NEL bytes ignored inside comments");
	}

	private static void fragmentOutputScan() {
		for (String source : List.of("void main() {}", "void main() { discard; }", "// gl_FragColor out\nvoid main() {}")) {
			if (dev.spacebod.aetherium.shaders.shaderpack.properties.ProgramDirectives.hasFragmentOutput(source))
				throw new AssertionError("placeholder added a phantom colour target");
		}
		for (String source : List.of("void main() { gl_FragColor = vec4(1); }", "void main() { gl_FragData[0] = vec4(1); }",
				"layout(location=0) out vec4 color; void main() { color=vec4(1); }")) {
			if (!dev.spacebod.aetherium.shaders.shaderpack.properties.ProgramDirectives.hasFragmentOutput(source))
				throw new AssertionError("real fragment output lost its default target");
		}
		System.out.println("fragment outputs: empty placeholders use no targets; legacy and modern colour outputs kept");
	}

	/** SEUS writes shadowcolor1 without declaring DRAWBUFFERS; shadow defaults differ from world/full-screen defaults. */
	private static void defaultShadowBuffers() {
		String fragment = "void main() { gl_FragData[0] = vec4(1); gl_FragData[1] = vec4(0.5); }";
		for (String name : List.of("shadow", "shadow_solid", "shadow_cutout", "shadow_water", "shadow_entities", "dh_shadow",
				"gbuffers_terrain", "composite", "shadowcomp")) {
			boolean shadow = List.of("shadow", "shadow_solid", "shadow_cutout", "shadow_water", "shadow_entities", "dh_shadow").contains(name);
			for (int variant = 0; variant < 3; variant++) {
				String text = switch (variant) {
					case 1 -> "/* DRAWBUFFERS:10 */\n" + fragment;
					case 2 -> "void main() { discard; }";
					default -> fragment;
				};
				int[] expected = variant == 1 ? new int[]{1, 0} : variant == 2 ? new int[0] : shadow ? new int[]{0, 1} : new int[]{0};
				var source = new dev.spacebod.aetherium.shaders.shaderpack.programs.ProgramSource(name,
						"void main() { gl_Position = vec4(0); }", null, null, null, text, null, null, null);
				if (!java.util.Arrays.equals(expected, source.getDirectives().getDrawBuffers())) {
					throw new AssertionError(name + " lost its " + (variant == 1 ? "explicit" : "default") + " colour targets");
				}
			}
		}
		System.out.println("shadow outputs: implicit shadowcolor0/1 retained; explicit targets and empty placeholders preserved");
	}

	private static void customExpressions() {
		try {
			var resolver = new kroppeb.stareval.resolver.ExpressionResolver(
					dev.spacebod.aetherium.shaders.parsing.ExpressionFunctions.functions, name -> null, true);
			for (String text : List.of("vec2(0.01, 0.02) * 0.51", "0.51 * vec2(0.01, 0.02)",
					"(vec2(0.01, 0.02) * 0.5) * 1.02")) {
				var parsed = kroppeb.stareval.parser.Parser.parse(text, dev.spacebod.aetherium.shaders.parsing.ExpressionOperators.options);
				var expression = resolver.resolveExpression(dev.spacebod.aetherium.shaders.parsing.VectorType.VEC2, parsed);
				var value = new kroppeb.stareval.function.FunctionReturn();
				expression.evaluateTo(null, value);
				var vector = (org.joml.Vector2f) value.objectReturn;
				if (Math.abs(vector.x - 0.0051f) > 1e-7f || Math.abs(vector.y - 0.0102f) > 1e-7f)
					throw new AssertionError("incorrect scalar/vector multiplication: " + text + " -> " + vector);
			}
		} catch (Exception e) {
			throw new AssertionError("MakeUp's TAA expression could not be resolved", e);
		}
		System.out.println("custom expressions: vector/scalar multiplication works in both orders and nested calls");
	}
}
