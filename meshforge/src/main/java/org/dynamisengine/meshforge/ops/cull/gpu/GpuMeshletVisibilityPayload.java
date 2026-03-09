package org.dynamisengine.meshforge.ops.cull.gpu;

import org.dynamisengine.meshforge.ops.compress.gpu.CompressedRuntimePayload;
import org.dynamisengine.meshforge.ops.compress.gpu.RuntimePayloadCompression;
import org.dynamisengine.meshforge.ops.compress.gpu.RuntimePayloadCompressionMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * GPU-ready meshlet visibility payload containing flattened meshlet bounds.
 *
 * Layout v1:
 * - meshlet bounds are packed as 6 float values per meshlet
 * - order per meshlet: minX, minY, minZ, maxX, maxY, maxZ
 * - little-endian byte export
 */
public record GpuMeshletVisibilityPayload(
    int meshletCount,
    int boundsOffsetFloats,
    int boundsStrideFloats,
    float[] boundsPayload
) {
    public static final int BOUNDS_COMPONENTS = 6;

    public GpuMeshletVisibilityPayload {
        if (meshletCount < 0) {
            throw new IllegalArgumentException("meshletCount must be >= 0");
        }
        if (boundsOffsetFloats < 0) {
            throw new IllegalArgumentException("boundsOffsetFloats must be >= 0");
        }
        if (boundsStrideFloats < BOUNDS_COMPONENTS) {
            throw new IllegalArgumentException("boundsStrideFloats must be >= " + BOUNDS_COMPONENTS);
        }
        if (boundsPayload == null) {
            throw new NullPointerException("boundsPayload");
        }

        int required = expectedBoundsPayloadLengthFloats(meshletCount, boundsOffsetFloats, boundsStrideFloats);
        if (boundsPayload.length != required) {
            throw new IllegalArgumentException(
                "boundsPayload length mismatch: expected=" + required + " actual=" + boundsPayload.length
            );
        }

        boundsPayload = boundsPayload.clone();
    }

    public int boundsByteSize() {
        return boundsFloatCount() * Float.BYTES;
    }

    public int boundsStrideBytes() {
        return boundsStrideFloats * Float.BYTES;
    }

    /**
     * Returns total payload float count.
     */
    public int boundsFloatCount() {
        return boundsPayload.length;
    }

    /**
     * Returns expected payload float count from meshlet metadata.
     */
    public int expectedBoundsPayloadLengthFloats() {
        return expectedBoundsPayloadLengthFloats(meshletCount, boundsOffsetFloats, boundsStrideFloats);
    }

    /**
     * Returns a defensive copy of raw bounds payload floats.
     */
    @Override
    public float[] boundsPayload() {
        return boundsPayload.clone();
    }

    /**
     * Returns a read-only little-endian float view over packed bounds payload.
     */
    public FloatBuffer toBoundsFloatBuffer() {
        return toBoundsByteBuffer().asFloatBuffer().asReadOnlyBuffer();
    }

    /**
     * Materializes a little-endian byte buffer suitable for upload.
     */
    public ByteBuffer toBoundsByteBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(boundsByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        out.asFloatBuffer().put(boundsPayload);
        out.position(0);
        out.limit(boundsByteSize());
        return out;
    }

    /**
     * Materializes optional compressed payload bytes while preserving canonical bounds semantics.
     */
    public CompressedRuntimePayload toCompressedBoundsPayload(RuntimePayloadCompressionMode mode) {
        if (mode == null) {
            throw new NullPointerException("mode");
        }
        byte[] compressed = RuntimePayloadCompression.compress(toBoundsByteBuffer(), mode);
        return new CompressedRuntimePayload(mode, boundsByteSize(), compressed);
    }

    private static int expectedBoundsPayloadLengthFloats(int meshletCount, int offset, int stride) {
        return meshletCount == 0 ? 0 : (offset + (meshletCount * stride));
    }
}
