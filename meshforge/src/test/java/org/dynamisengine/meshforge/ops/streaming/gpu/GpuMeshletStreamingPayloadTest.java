package org.dynamisengine.meshforge.ops.streaming.gpu;

import org.dynamisengine.meshforge.ops.compress.gpu.CompressedRuntimePayload;
import org.dynamisengine.meshforge.ops.compress.gpu.RuntimePayloadCompressionMode;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GpuMeshletStreamingPayloadTest {

    @Test
    void rejectsMismatchedPayloadLength() {
        assertThrows(IllegalArgumentException.class,
            () -> new GpuMeshletStreamingPayload(2, 0, 5, new int[5]));
    }

    @Test
    void rejectsStrideBelowRequiredComponents() {
        assertThrows(IllegalArgumentException.class,
            () -> new GpuMeshletStreamingPayload(1, 0, 4, new int[4]));
    }

    @Test
    void reportsExpectedMetadataAndByteContract() {
        GpuMeshletStreamingPayload payload = new GpuMeshletStreamingPayload(
            2,
            0,
            5,
            new int[] {0, 0, 32, 0, 4096, 1, 32, 16, 4096, 2048}
        );

        assertEquals(10, payload.unitsIntCount());
        assertEquals(10, payload.expectedUnitsPayloadLengthInts());
        assertEquals(40, payload.unitsByteSize());
        assertEquals(20, payload.unitsStrideBytes());
        assertEquals(10, payload.toUnitsIntBuffer().remaining());
    }

    @Test
    void unitsPayloadAccessorReturnsDefensiveCopy() {
        GpuMeshletStreamingPayload payload = new GpuMeshletStreamingPayload(
            1,
            0,
            5,
            new int[] {0, 0, 32, 0, 4096}
        );

        int[] copy = payload.unitsPayload();
        copy[0] = 999;

        assertArrayEquals(new int[] {0, 0, 32, 0, 4096}, payload.unitsPayload());
    }

    @Test
    void byteBufferExportMatchesPayloadOrder() {
        GpuMeshletStreamingPayload payload = new GpuMeshletStreamingPayload(
            1,
            0,
            5,
            new int[] {2, 96, 8, 8192, 1024}
        );

        ByteBuffer bytes = payload.toUnitsByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(20, bytes.remaining());
        assertEquals(2, bytes.getInt());
        assertEquals(96, bytes.getInt());
        assertEquals(8, bytes.getInt());
        assertEquals(8192, bytes.getInt());
        assertEquals(1024, bytes.getInt());
    }

    @Test
    void supportsOptionalDeflateCompressedExportRoundTrip() {
        GpuMeshletStreamingPayload payload = new GpuMeshletStreamingPayload(
            2,
            0,
            5,
            new int[] {0, 0, 32, 0, 4096, 1, 32, 16, 4096, 2048}
        );

        byte[] canonical = toArray(payload.toUnitsByteBuffer());
        CompressedRuntimePayload compressed = payload.toCompressedUnitsPayload(RuntimePayloadCompressionMode.DEFLATE);
        byte[] restored = compressed.toUncompressedBytes();

        assertEquals(RuntimePayloadCompressionMode.DEFLATE, compressed.mode());
        assertEquals(payload.unitsByteSize(), compressed.uncompressedByteSize());
        assertArrayEquals(canonical, restored);
        assertTrue(compressed.payloadBytes().length <= canonical.length);
    }

    @Test
    void supportsOptionalNoneCompressedExportWithoutSemanticChange() {
        GpuMeshletStreamingPayload payload = new GpuMeshletStreamingPayload(
            1,
            0,
            5,
            new int[] {2, 96, 8, 8192, 1024}
        );

        byte[] canonical = toArray(payload.toUnitsByteBuffer());
        CompressedRuntimePayload noneCompressed = payload.toCompressedUnitsPayload(RuntimePayloadCompressionMode.NONE);

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
