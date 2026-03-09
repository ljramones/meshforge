package org.dynamisengine.meshforge.ops.lod.gpu;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuMeshletLodPayloadTest {

    @Test
    void rejectsMismatchedPayloadLength() {
        assertThrows(IllegalArgumentException.class,
            () -> new GpuMeshletLodPayload(2, 0, 4, new int[4]));
    }

    @Test
    void rejectsStrideBelowRequiredComponents() {
        assertThrows(IllegalArgumentException.class,
            () -> new GpuMeshletLodPayload(1, 0, 3, new int[3]));
    }

    @Test
    void reportsExpectedMetadataAndByteContract() {
        GpuMeshletLodPayload payload = new GpuMeshletLodPayload(
            2,
            0,
            4,
            new int[] {0, 0, 64, Float.floatToRawIntBits(0.0f), 1, 64, 32, Float.floatToRawIntBits(0.75f)}
        );

        assertEquals(8, payload.levelsIntCount());
        assertEquals(8, payload.expectedLevelsPayloadLengthInts());
        assertEquals(32, payload.levelsByteSize());
        assertEquals(16, payload.levelsStrideBytes());
        assertEquals(8, payload.toLevelsIntBuffer().remaining());
    }

    @Test
    void levelsPayloadAccessorReturnsDefensiveCopy() {
        GpuMeshletLodPayload payload = new GpuMeshletLodPayload(
            1,
            0,
            4,
            new int[] {0, 0, 16, Float.floatToRawIntBits(0.5f)}
        );

        int[] copy = payload.levelsPayload();
        copy[0] = 999;

        assertArrayEquals(new int[] {0, 0, 16, Float.floatToRawIntBits(0.5f)}, payload.levelsPayload());
    }

    @Test
    void byteBufferExportMatchesPayloadOrder() {
        int errorBits = Float.floatToRawIntBits(1.25f);
        GpuMeshletLodPayload payload = new GpuMeshletLodPayload(
            1,
            0,
            4,
            new int[] {2, 96, 8, errorBits}
        );

        ByteBuffer bytes = payload.toLevelsByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(16, bytes.remaining());
        assertEquals(2, bytes.getInt());
        assertEquals(96, bytes.getInt());
        assertEquals(8, bytes.getInt());
        assertEquals(errorBits, bytes.getInt());
    }
}
