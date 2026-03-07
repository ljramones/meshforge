package org.dynamisengine.meshforge.loader.gltf.read;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Lz4BlockDecompressorTest {
    @Test
    void decodesLiteralOnlyBlock() {
        byte[] encoded = new byte[]{
            (byte) (5 << 4),
            'H', 'e', 'l', 'l', 'o'
        };
        byte[] decoded = Lz4BlockDecompressor.decompress(encoded, 5);
        assertArrayEquals("Hello".getBytes(StandardCharsets.US_ASCII), decoded);
    }

    @Test
    void decodesLiteralAndMatchBlock() {
        byte[] encoded = new byte[]{
            (byte) ((3 << 4) | 2),
            'a', 'b', 'c',
            3, 0
        };
        byte[] decoded = Lz4BlockDecompressor.decompress(encoded, 9);
        assertArrayEquals("abcabcabc".getBytes(StandardCharsets.US_ASCII), decoded);
    }

    @Test
    void rejectsTruncatedInput() {
        byte[] encoded = new byte[]{
            (byte) (4 << 4),
            'a', 'b'
        };
        assertThrows(IllegalArgumentException.class, () -> Lz4BlockDecompressor.decompress(encoded, 4));
    }
}
