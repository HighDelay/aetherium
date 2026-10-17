package dev.spacebod.aetherium.client.mixin.shaders;

import dev.spacebod.aetherium.shaders.pbr.PbrTextures;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aetherium Shaders: atlases may have material maps ({@link PbrTextures}), which animate with them. */
@Mixin(TextureAtlas.class)
abstract class PbrTextureAtlasMixin extends AbstractTexture {
	@Inject(method = "upload", at = @At("RETURN"))
	private void aetherium$track(CallbackInfo ci) {
		PbrTextures.track((TextureAtlas) (Object) this);
	}

	@Inject(method = "cycleAnimationFrames", at = @At("TAIL"))
	private void aetherium$animateMaps(CallbackInfo ci) {
		// An atlas can tick before its first upload (NeoForge ticks textures while resources are still loading); it has
		// no GPU texture yet, and so no material maps to animate.
		if (texture != null) {
			PbrTextures.cycleAnimations((TextureAtlas) (Object) this);
		}
	}
}
