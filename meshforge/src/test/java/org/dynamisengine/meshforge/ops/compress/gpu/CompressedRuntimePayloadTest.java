package org.dynamisengine.meshforge.ops.compress.gpu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressedRuntimePayloadTest {
    @Test
    void noneModeRoundTripPreservesBytes() {
        byte[] raw = new byte[] {1, 2, 3, 4, 5};
        CompressedRuntimePayload payload =
            new CompressedRuntimePayload(RuntimePayloadCompressionMode.NONE, raw.length, raw);

        assertArrayEquals(raw, payload.toUncompressedBytes());
    }

    @Test
    void rejectsDecompressionSizeMismatch() {
        byte[] raw = new byte[] {10, 11, 12, 13, 14, 15};
        byte[] compressed = RuntimePayloadCompression.compress(raw, RuntimePayloadCompressionMode.DEFLATE);
        CompressedRuntimePayload payload =
            new CompressedRuntimePayload(RuntimePayloadCompressionMode.DEFLATE, raw.length + 1, compressed);

        assertThrows(IllegalArgumentException.class, payload::toUncompressedBytes);
    }
}

