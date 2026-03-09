package org.dynamisengine.meshforge.ops.cull.gpu;

import org.dynamisengine.meshforge.ops.compress.gpu.CompressedRuntimePayload;
import org.dynamisengine.meshforge.ops.compress.gpu.RuntimePayloadCompressionMode;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuMeshletVisibilityPayloadTest {

    @Test
    void rejectsMismatchedPayloadLength() {
        assertThrows(IllegalArgumentException.class,
            () -> new GpuMeshletVisibilityPayload(2, 0, 6, new float[6]));
    }

    @Test
    void rejectsStrideBelowRequiredComponents() {
        assertThrows(IllegalArgumentException.class,
            () -> new GpuMeshletVisibilityPayload(1, 0, 5, new float[5]));
    }

    @Test
    void reportsExpectedMetadataAndByteContract() {
        GpuMeshletVisibilityPayload payload = new GpuMeshletVisibilityPayload(
            2,
            0,
            6,
            new float[] {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f}
        );

        assertEquals(12, payload.boundsFloatCount());
        assertEquals(12, payload.expectedBoundsPayloadLengthFloats());
        assertEquals(48, payload.boundsByteSize());
        assertEquals(24, payload.boundsStrideBytes());
        assertEquals(12, payload.toBoundsFloatBuffer().remaining());
    }

    @Test
    void boundsPayloadAccessorReturnsDefensiveCopy() {
        GpuMeshletVisibilityPayload payload = new GpuMeshletVisibilityPayload(
            1,
            0,
            6,
            new float[] {1f, 2f, 3f, 4f, 5f, 6f}
        );

        float[] copy = payload.boundsPayload();
        copy[0] = 999f;

        assertArrayEquals(new float[] {1f, 2f, 3f, 4f, 5f, 6f}, payload.boundsPayload());
    }

    @Test
    void byteBufferExportMatchesFloatPayloadOrder() {
        GpuMeshletVisibilityPayload payload = new GpuMeshletVisibilityPayload(
            1,
            0,
            6,
            new float[] {1f, 2f, 3f, 4f, 5f, 6f}
        );

        ByteBuffer bytes = payload.toBoundsByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(24, bytes.remaining());
        assertEquals(1f, bytes.getFloat());
        assertEquals(2f, bytes.getFloat());
        assertEquals(3f, bytes.getFloat());
        assertEquals(4f, bytes.getFloat());
        assertEquals(5f, bytes.getFloat());
        assertEquals(6f, bytes.getFloat());
    }

    @Test
    void supportsOptionalDeflateCompressedExportRoundTrip() {
        GpuMeshletVisibilityPayload payload = new GpuMeshletVisibilityPayload(
            2,
            0,
            6,
            new float[] {0f, 1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f}
        );

        byte[] canonical = toArray(payload.toBoundsByteBuffer());
        CompressedRuntimePayload compressed = payload.toCompressedBoundsPayload(RuntimePayloadCompressionMode.DEFLATE);
        byte[] restored = compressed.toUncompressedBytes();

        assertEquals(RuntimePayloadCompressionMode.DEFLATE, compressed.mode());
        assertEquals(payload.boundsByteSize(), compressed.uncompressedByteSize());
        assertArrayEquals(canonical, restored);
        assertTrue(compressed.payloadBytes().length <= canonical.length);
    }

    @Test
    void supportsOptionalNoneCompressedExportWithoutSemanticChange() {
        GpuMeshletVisibilityPayload payload = new GpuMeshletVisibilityPayload(
            1,
            0,
            6,
            new float[] {1f, 2f, 3f, 4f, 5f, 6f}
        );

        byte[] canonical = toArray(payload.toBoundsByteBuffer());
        CompressedRuntimePayload noneCompressed = payload.toCompressedBoundsPayload(RuntimePayloadCompressionMode.NONE);

        assertEquals(RuntimePayloadCompressionMode.NONE, noneCompressed.mode());
        assertArrayEquals(canonical, noneCompressed.payloadBytes());
        assertArrayEquals(canonical, noneCompressed.toUncompressedBytes());
    }

    private static byte[] toArray(ByteBuffer bytes) {
        ByteBuffer view = bytes.asReadOnlyBuffer();
        byte[] out = new byte[view.remaining()];
        view.get(out);
        return out;
    }
}
