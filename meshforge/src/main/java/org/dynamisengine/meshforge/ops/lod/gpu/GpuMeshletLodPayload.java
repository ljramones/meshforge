package org.dynamisengine.meshforge.ops.lod.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * GPU-ready meshlet LOD metadata payload.
 *
 * Layout v1 (int32 words per level):
 * - lodLevel
 * - meshletStart
 * - meshletCount
 * - geometricError encoded as {@code Float.floatToRawIntBits(error)}
 */
public record GpuMeshletLodPayload(
    int levelCount,
    int levelsOffsetInts,
    int levelsStrideInts,
    int[] levelsPayload
) {
    public static final int LEVEL_COMPONENTS = 4;

    public GpuMeshletLodPayload {
        if (levelCount < 0) {
            throw new IllegalArgumentException("levelCount must be >= 0");
        }
        if (levelsOffsetInts < 0) {
            throw new IllegalArgumentException("levelsOffsetInts must be >= 0");
        }
        if (levelsStrideInts < LEVEL_COMPONENTS) {
            throw new IllegalArgumentException("levelsStrideInts must be >= " + LEVEL_COMPONENTS);
        }
        if (levelsPayload == null) {
            throw new NullPointerException("levelsPayload");
        }

        int required = expectedLevelsPayloadLengthInts(levelCount, levelsOffsetInts, levelsStrideInts);
        if (levelsPayload.length != required) {
            throw new IllegalArgumentException(
                "levelsPayload length mismatch: expected=" + required + " actual=" + levelsPayload.length
            );
        }

        levelsPayload = levelsPayload.clone();
    }

    public int levelsIntCount() {
        return levelsPayload.length;
    }

    public int levelsByteSize() {
        return levelsIntCount() * Integer.BYTES;
    }

    public int levelsStrideBytes() {
        return levelsStrideInts * Integer.BYTES;
    }

    public int expectedLevelsPayloadLengthInts() {
        return expectedLevelsPayloadLengthInts(levelCount, levelsOffsetInts, levelsStrideInts);
    }

    @Override
    public int[] levelsPayload() {
        return levelsPayload.clone();
    }

    public IntBuffer toLevelsIntBuffer() {
        return toLevelsByteBuffer().asIntBuffer().asReadOnlyBuffer();
    }

    public ByteBuffer toLevelsByteBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(levelsByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : levelsPayload) {
            out.putInt(value);
        }
        out.flip();
        return out;
    }

    private static int expectedLevelsPayloadLengthInts(int levelCount, int offset, int stride) {
        return levelCount == 0 ? 0 : (offset + (levelCount * stride));
    }
}
