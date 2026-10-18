package dev.spacebod.aetherium.client.mixin.shaders.ids;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.spacebod.aetherium.shaders.engine.ShaderPackEngine;
import dev.spacebod.aetherium.shaders.helpers.DrawIds;
import dev.spacebod.aetherium.shaders.uniforms.DrawState;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aetherium Shaders: while a feature render type draws, the engine knows it (the shadow directives filter feature
 * draws) and whether it is block-entity geometry (its pipeline gets the block-entity program). An enchanted item's draw
 * is followed by its glint drawn with the pack's glint program, when the pack has one (ShaderPackEngine.glintPass).
 */
@Mixin(PreparedRenderType.class)
abstract class PreparedRenderTypeMixin {
	@Shadow
	@Final
	private String name;
	@Shadow
	@Final
	private java.util.List<PreparedRenderType.Texture> textures;
	@Shadow
	@Final
	private GpuBufferSlice dynamicTransforms;

	@Inject(method = "draw", at = @At("HEAD"))
	private void aetherium$beginDraw(StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass, RenderPipeline renderPipeline, CallbackInfo ci) {
		ShaderPackEngine engine = ShaderPackEngine.get();
		if (!engine.running()) {
			return;
		}
		engine.setFeatureDraw(true, name.startsWith(DrawIds.PREFIX) ? Character.digit(name.charAt(DrawIds.PREFIX.length()), 16) : 0);
		for (PreparedRenderType.Texture texture : textures) {
			if (texture.name().equals("Sampler0")) {
				DrawState.setAlbedo(texture.textureView());
				break;
			}
		}
	}

	/** The same geometry again with the glint program, the glint texture as its albedo. */
	@Inject(method = "draw", at = @At(value = "INVOKE",
			target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V", shift = At.Shift.AFTER))
	private void aetherium$glint(StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass, RenderPipeline renderPipeline, CallbackInfo ci) {
		ShaderPackEngine engine = ShaderPackEngine.get();
		if (!engine.running()) {
			return;
		}
		CompiledRenderPipeline glint = engine.glintPass(renderPipeline);
		if (glint == null) {
			return;
		}
		PreparedRenderType.Texture glintTexture = null;
		for (PreparedRenderType.Texture texture : textures) {
			if (texture.name().equals("GlintSampler")) {
				glintTexture = texture;
				break;
			}
		}
		if (glintTexture == null) {
			return;
		}
		DrawState.setAlbedo(glintTexture.textureView());
		renderPass.setPipeline(glint);
		RenderSystem.bindDefaultUniforms(renderPass);
		renderPass.setUniform("DynamicTransforms", dynamicTransforms);
		renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
		for (PreparedRenderType.Texture texture : textures) {
			if (!texture.name().equals("Sampler0") && texture != glintTexture) {
				renderPass.setUniform(texture.name(), texture.textureView(), texture.sampler());
			}
		}
		renderPass.setUniform("Sampler0", glintTexture.textureView(), glintTexture.sampler());
		renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
		renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
	}

	/** Unconditional (two field writes): the pack can stop during the draw. */
	@Inject(method = "draw", at = @At("RETURN"))
	private void aetherium$endDraw(StagedVertexBuffer.ExecuteInfo info, RenderPass renderPass, RenderPipeline renderPipeline, CallbackInfo ci) {
		ShaderPackEngine.get().setFeatureDraw(false, 0);
		DrawState.setAlbedo(null);
	}
}
