package dev.spacebod.aetherium.shaders.engine;

import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;

/**
 * The block-breaking crack drawn after the deferred passes, with the breaking overlay, whatever block it lies on. 26.3
 * files the crack over a solid block among the solid features, before the deferred passes; packs that multiply their
 * crack into the shaded colour (colortex0 rewritten by their deferred pass) then never show it. While the level's cracks
 * are submitted, a submit to a collection's solid phase goes to that collection's breaking overlay instead, whichever
 * submit method a loader's renderer API routes the crack through. Render thread only.
 */
public final class CrackRouting {
	private static boolean submitting;

	private CrackRouting() {
	}

	/** A solid feature phase, linked to the breaking overlay of its collection. */
	public interface SolidPhase {
		void aetherium$linkBreakingOverlay(SimpleFeatureRenderPhase breakingOverlay);
	}

	/** Around the level's block-breaking submits. */
	public static void submitting(boolean on) {
		submitting = on;
	}

	/** Whether a submit to a solid phase now goes to its breaking overlay. */
	public static boolean redirect() {
		return submitting && ShaderPackEngine.get().running();
	}
}
