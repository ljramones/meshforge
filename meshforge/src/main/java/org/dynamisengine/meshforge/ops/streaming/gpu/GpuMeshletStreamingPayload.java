package org.dynamisengine.meshforge.ops.streaming.gpu;

import org.dynamisengine.meshforge.ops.compress.gpu.CompressedRuntimePayload;
import org.dynamisengine.meshforge.ops.compress.gpu.RuntimePayloadCompression;
import org.dynamisengine.meshforge.ops.compress.gpu.RuntimePayloadCompressionMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * GPU-ready meshlet streaming metadata payload.
 *
 * Layout v1 (int32 words per unit):
 * - streamUnitId
 * - meshletStart
 * - meshletCount
 * - payloadByteOffset
 * - payloadByteSize
 */
public record GpuMeshletStreamingPayload(
    int unitCount,
    int unitsOffsetInts,
    int unitsStrideInts,
    int[] unitsPayload
) {
    public static final int UNIT_COMPONENTS = 5;

    public GpuMeshletStreamingPayload {
        if (unitCount < 0) {
            throw new IllegalArgumentException("unitCount must be >= 0");
        }
        if (unitsOffsetInts < 0) {
            throw new IllegalArgumentException("unitsOffsetInts must be >= 0");
        }
        if (unitsStrideInts < UNIT_COMPONENTS) {
            throw new IllegalArgumentException("unitsStrideInts must be >= " + UNIT_COMPONENTS);
        }
        if (unitsPayload == null) {
            throw new NullPointerException("unitsPayload");
        }

        int required = expectedUnitsPayloadLengthInts(unitCount, unitsOffsetInts, unitsStrideInts);
        if (unitsPayload.length != required) {
            throw new IllegalArgumentException(
                "unitsPayload length mismatch: expected=" + required + " actual=" + unitsPayload.length
            );
        }

        unitsPayload = unitsPayload.clone();
    }

    public int unitsIntCount() {
        return unitsPayload.length;
    }

    public int unitsByteSize() {
        return unitsIntCount() * Integer.BYTES;
    }

    public int unitsStrideBytes() {
        return unitsStrideInts * Integer.BYTES;
    }

    public int expectedUnitsPayloadLengthInts() {
        return expectedUnitsPayloadLengthInts(unitCount, unitsOffsetInts, unitsStrideInts);
    }

    @Override
    public int[] unitsPayload() {
        return unitsPayload.clone();
    }

    public IntBuffer toUnitsIntBuffer() {
        return toUnitsByteBuffer().asIntBuffer().asReadOnlyBuffer();
    }

    public ByteBuffer toUnitsByteBuffer() {
        ByteBuffer out = ByteBuffer.allocateDirect(unitsByteSize()).order(ByteOrder.LITTLE_ENDIAN);
        out.asIntBuffer().put(unitsPayload);
        out.position(0);
        out.limit(unitsByteSize());
        return out;
    }

    /**
     * Materializes optional compressed payload bytes while preserving canonical streaming semantics.
     */
    public CompressedRuntimePayload toCompressedUnitsPayload(RuntimePayloadCompressionMode mode) {
        if (mode == null) {
            throw new NullPointerException("mode");
        }
        byte[] compressed = RuntimePayloadCompression.compress(toUnitsByteBuffer(), mode);
        return new CompressedRuntimePayload(mode, unitsByteSize(), compressed);
    }

    private static int expectedUnitsPayloadLengthInts(int unitCount, int offset, int stride) {
        return unitCount == 0 ? 0 : (offset + (unitCount * stride));
    }
}
