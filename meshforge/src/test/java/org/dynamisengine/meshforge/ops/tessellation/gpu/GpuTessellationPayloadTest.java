package org.dynamisengine.meshforge.ops.tessellation.gpu;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GpuTessellationPayloadTest {

    @Test
    void rejectsMismatchedPayloadLength() {
        assertThrows(IllegalArgumentException.class,
            () -> new GpuTessellationPayload(2, 0, 6, new int[6]));
    }

    @Test
    void rejectsStrideBelowRequiredComponents() {
        assertThrows(IllegalArgumentException.class,
            () -> new GpuTessellationPayload(1, 0, 5, new int[5]));
    }

    @Test
    void reportsExpectedMetadataAndByteContract() {
        GpuTessellationPayload payload = new GpuTessellationPayload(
            2,
            0,
            6,
            new int[] {
                0, 0, 12, 3, Float.floatToRawIntBits(1.0f), 0,
                1, 12, 9, 4, Float.floatToRawIntBits(2.0f), 1
            }
        );

        assertEquals(12, payload.regionsIntCount());
        assertEquals(12, payload.expectedRegionsPayloadLengthInts());
        assertEquals(48, payload.regionsByteSize());
        assertEquals(24, payload.regionsStrideBytes());
        assertEquals(12, payload.toRegionsIntBuffer().remaining());
    }

    @Test
    void regionsPayloadAccessorReturnsDefensiveCopy() {
        GpuTessellationPayload payload = new GpuTessellationPayload(
            1,
            0,
            6,
            new int[] {0, 0, 12, 3, Float.floatToRawIntBits(1.0f), 0}
        );

        int[] copy = payload.regionsPayload();
        copy[0] = 999;

        assertArrayEquals(
            new int[] {0, 0, 12, 3, Float.floatToRawIntBits(1.0f), 0},
            payload.regionsPayload()
        );
    }

    @Test
    void byteBufferExportMatchesPayloadOrder() {
        int tessLevelBits = Float.floatToRawIntBits(2.5f);
        GpuTessellationPayload payload = new GpuTessellationPayload(
            1,
            0,
            6,
            new int[] {2, 24, 15, 4, tessLevelBits, 3}
        );

        ByteBuffer bytes = payload.toRegionsByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(24, bytes.remaining());
        assertEquals(2, bytes.getInt());
        assertEquals(24, bytes.getInt());
        assertEquals(15, bytes.getInt());
        assertEquals(4, bytes.getInt());
        assertEquals(tessLevelBits, bytes.getInt());
        assertEquals(3, bytes.getInt());
    }
}
