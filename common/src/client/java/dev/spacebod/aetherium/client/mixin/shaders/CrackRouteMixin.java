package dev.spacebod.aetherium.client.mixin.shaders;

import dev.spacebod.aetherium.shaders.engine.CrackRouting;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aetherium Shaders: a block crack submitted to a solid phase goes to its collection's breaking overlay ({@link CrackRouting}). */
@Mixin(SimpleFeatureRenderPhase.class)
abstract class CrackRouteMixin implements CrackRouting.SolidPhase {
	@Unique
	private @Nullable SimpleFeatureRenderPhase aetherium$breakingOverlay;

	@Override
	public void aetherium$linkBreakingOverlay(SimpleFeatureRenderPhase breakingOverlay) {
		aetherium$breakingOverlay = breakingOverlay;
	}

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void aetherium$crackAfterDeferred(SubmitNode submit, CallbackInfo ci) {
		SimpleFeatureRenderPhase overlay = aetherium$breakingOverlay;
		if (overlay != null && overlay != (Object) this && CrackRouting.redirect()) {
			overlay.submit(submit);
			ci.cancel();
		}
	}
}
