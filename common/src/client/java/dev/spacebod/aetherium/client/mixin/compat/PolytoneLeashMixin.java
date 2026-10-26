package dev.spacebod.aetherium.client.mixin.compat;

import dev.spacebod.aetherium.shaders.engine.ShaderPackEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Polytone's textured lead uses vanilla colour output; shader packs need the ordinary lead path, as with Iris. */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.polytone.PolytoneRenderTypes", remap = false)
abstract class PolytoneLeashMixin {
	@Inject(method = "isLeashRenderOn", at = @At("HEAD"), cancellable = true, require = 0)
	private static void aetherium$vanillaLeashUnderPack(CallbackInfoReturnable<Boolean> cir) {
		if (ShaderPackEngine.get().packActive()) {
			cir.setReturnValue(false);
		}
	}
}
