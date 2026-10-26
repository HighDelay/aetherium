package dev.spacebod.aetherium.shaders.engine;

import java.nio.ByteBuffer;
import org.jspecify.annotations.Nullable;

/**
 * The std140 {@code AetheriumUniforms} bytes of the program whose uniforms are being updated. The uniform
 * classes write through {@code ShaderRenderSystem.uniformXxx(location, ...)} with the member's byte offset as the
 * location; this routes those writes into the right buffer. Render thread only.
 */
public final class UniformBlockBuffer {
	/** Tagged scalar locations keep cached integer/boolean providers usable for a pack's float declaration. */
	private static final int INT_TO_FLOAT = 1 << 30;
	private static @Nullable ByteBuffer current;

	private UniformBlockBuffer() {
	}

	/** Makes {@code buffer} the target of uniform writes until {@link #end()}. */
	public static void begin(ByteBuffer buffer) {
		current = buffer;
	}

	public static void end() {
		current = null;
	}

	public static int intToFloatLocation(int offset) {
		return offset | INT_TO_FLOAT;
	}

	public static void putInt(int location, int value) {
		int offset = location & ~INT_TO_FLOAT;
		ByteBuffer buffer = target(offset);
		if (buffer != null) {
			if ((location & INT_TO_FLOAT) != 0) {
				buffer.putFloat(offset, value);
			} else {
				buffer.putInt(offset, value);
			}
		}
	}

	/** The buffer a write at {@code offset} goes to, or null when nothing is bound or the uniform is not in the block. */
	public static @Nullable ByteBuffer target(int offset) {
		ByteBuffer b = current;
		return b == null || offset < 0 || offset >= b.capacity() ? null : b;
	}
}
