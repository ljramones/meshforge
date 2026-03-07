package org.dynamisengine.meshforge.loader.gltf.read;

import org.junit.jupiter.api.Test;
import org.dynamisengine.vectrix.gpu.OctaNormal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeshoptDecoderTest {
    @Test
    void decodesFilterNoneAttributes() {
        byte[] raw = new byte[]{1, 2, 3, 4};
        MeshoptDecodeRequest request = new MeshoptDecodeRequest(
            lz4LiteralBlock(raw),
            raw.length,
            1,
            4,
            MeshoptCompressionMode.ATTRIBUTES,
            MeshoptCompressionFilter.NONE
        );

        MeshoptDecodeResult result = MeshoptDecoder.decode(request);
        assertEquals(1, result.count());
        assertEquals(4, result.byteStride());
        assertArrayEquals(raw, result.data());
    }

    @Test
    void decodesOctahedralNormalsToFloat3() {
        byte[] packed = new byte[8];
        ByteBuffer bb = ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
        putPackedOcta(bb, 0f, 0f, 1f);
        putPackedOcta(bb, 1f, 0f, 0f);

        MeshoptDecodeRequest request = new MeshoptDecodeRequest(
            lz4LiteralBlock(packed),
            packed.length,
            2,
            4,
            MeshoptCompressionMode.ATTRIBUTES,
            MeshoptCompressionFilter.OCTAHEDRAL
        );

        MeshoptDecodeResult result = MeshoptDecoder.decode(request);
        assertEquals(2, result.count());
        assertEquals(12, result.byteStride());

        ByteBuffer out = ByteBuffer.wrap(result.data()).order(ByteOrder.LITTLE_ENDIAN);
        assertClose(out.getFloat(), 0f, 1.0e-3f);
        assertClose(out.getFloat(), 0f, 1.0e-3f);
        assertClose(out.getFloat(), 1f, 1.0e-3f);
        assertClose(out.getFloat(), 1f, 1.0e-2f);
        assertClose(out.getFloat(), 0f, 1.0e-2f);
        assertClose(out.getFloat(), 0f, 1.0e-2f);
    }

    @Test
    void rejectsOctahedralDecodeForTriangleMode() {
        MeshoptDecodeRequest request = new MeshoptDecodeRequest(
            lz4LiteralBlock(new byte[4]),
            4,
            1,
            4,
            MeshoptCompressionMode.TRIANGLES,
            MeshoptCompressionFilter.OCTAHEDRAL
        );
        assertThrows(IllegalArgumentException.class, () -> MeshoptDecoder.decode(request));
    }

    private static byte[] lz4LiteralBlock(byte[] payload) {
        int len = payload.length;
        if (len < 15) {
            byte[] out = new byte[1 + len];
            out[0] = (byte) (len << 4);
            System.arraycopy(payload, 0, out, 1, len);
            return out;
        }
        int ext = len - 15;
        int extCount = (ext / 255) + 1;
        byte[] out = new byte[1 + extCount + len];
        out[0] = (byte) 0xF0;
        int pos = 1;
        while (ext >= 255) {
            out[pos++] = (byte) 255;
            ext -= 255;
        }
        out[pos++] = (byte) ext;
        System.arraycopy(payload, 0, out, pos, len);
        return out;
    }

    private static void putPackedOcta(ByteBuffer bb, float x, float y, float z) {
        int packed = OctaNormal.encodeSnorm16(x, y, z);
        bb.putShort((short) (packed & 0xFFFF));
        bb.putShort((short) ((packed >>> 16) & 0xFFFF));
    }

    private static void assertClose(float actual, float expected, float tolerance) {
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError("Expected " + expected + " ± " + tolerance + " but was " + actual);
        }
    }
}
