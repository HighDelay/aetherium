package dev.spacebod.aetherium.microbench;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

public final class ModCompatibilityFixtures {
	private ModCompatibilityFixtures() {
	}

	public static void main(String[] args) {
		net.minecraft.SharedConstants.tryDetectVersion();
		net.minecraft.server.Bootstrap.bootStrap();
		copiedPipelines();
	}

	/** Cosmetic mods copy vanilla pipelines and change fixed render state; their shader program must follow the copy. */
	private static void copiedPipelines() {
		var original = net.minecraft.client.renderer.RenderPipelines.ENTITY_TRANSLUCENT;
		var copy = new PipelineCopy(original, original.getShaders(), original.getShaderDefines(), original.getVertexFormatBindings());
		for (var context : List.of(dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.Context.WORLD,
				dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.Context.HAND,
				dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.Context.HAND_TRANSLUCENT,
				dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.Context.BLOCK_ENTITY)) {
			if (dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.main(copy, context)
					!= dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.main(original, context)
					|| dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.shadow(copy, context)
					!= dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.shadow(original, context)) {
				throw new AssertionError("copied entity pipeline lost its world or shadow program: " + context);
			}
		}
		var shaders = new java.util.EnumMap<>(original.getShaders());
		shaders.put(com.mojang.renderpearl.api.pipeline.ShaderType.FRAGMENT, Identifier.fromNamespaceAndPath("fixture", "custom"));
		var defines = original.getShaderDefines().withOverrides(new net.minecraft.client.renderer.ShaderDefines(Map.of(), Set.of("CUSTOM")));
		for (var custom : List.of(
				new PipelineCopy(original, shaders, original.getShaderDefines(), original.getVertexFormatBindings()),
				new PipelineCopy(original, original.getShaders(), defines, original.getVertexFormatBindings()),
				new PipelineCopy(original, original.getShaders(), original.getShaderDefines(), List.of(DefaultVertexFormat.POSITION)))) {
			if (dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.main(custom,
					dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.Context.WORLD) != null
					|| dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.shadow(custom,
					dev.spacebod.aetherium.shaders.pipeline.PipelinePrograms.Context.WORLD) != null) {
				throw new AssertionError("a changed shader interface was mistaken for a vanilla pipeline copy");
			}
		}
		System.out.println("pipeline copies: cosmetic render-state variants inherit world/shadow programs; custom shader interfaces stay separate");
	}

	private static final class PipelineCopy extends RenderPipeline {
		PipelineCopy(RenderPipeline original, Map<com.mojang.renderpearl.api.pipeline.ShaderType, Identifier> shaders,
				net.minecraft.client.renderer.ShaderDefines defines, List<VertexFormat> formats) {
			super(original.getLocation(), shaders, defines, original.getBindGroupLayouts(),
					original.getColorTargetStates().toArray(ColorTargetState[]::new), original.getDepthStencilState(),
					original.getPolygonMode(), !original.isCull(), formats.toArray(VertexFormat[]::new),
					original.getPrimitiveTopology(), original.pushConstantSize(), original.getSortKey());
		}
	}
}
