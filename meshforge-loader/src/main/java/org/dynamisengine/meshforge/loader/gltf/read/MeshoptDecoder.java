package org.dynamisengine.meshforge.loader.gltf.read;

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.gpu.OctaNormal;

/**
 * Minimal pure-Java meshopt decode path used by glTF loader integration.
 * <p>
 * Supported currently:
 * <ul>
 *     <li>LZ4 block decompression</li>
 *     <li>FILTER_NONE for ATTRIBUTES and TRIANGLES payloads</li>
 *     <li>FILTER_OCTAHEDRAL for ATTRIBUTES payloads encoded as SNORM16x2</li>
 * </ul>
 */
public final class MeshoptDecoder {
    private static final ThreadLocal<byte[]> DECOMPRESS_SCRATCH = ThreadLocal.withInitial(() -> new byte[0]);
    private static final ThreadLocal<Vector3f> OCTA_TMP = ThreadLocal.withInitial(Vector3f::new);

    private MeshoptDecoder() {
    }

    /**
     * Executes decode.
     * @param request parameter value
     * @return resulting value
     */
    public static MeshoptDecodeResult decode(MeshoptDecodeRequest request) {
        if (request.filter() == MeshoptCompressionFilter.NONE) {
            byte[] decompressed = Lz4BlockDecompressor.decompress(
                request.compressedPayload(),
                request.decompressedSize());
            return new MeshoptDecodeResult(decompressed, request.count(), request.byteStride());
        }

        if (request.filter() == MeshoptCompressionFilter.OCTAHEDRAL) {
            return decodeOctahedralAttributes(request);
        }

        throw new IllegalArgumentException("Unsupported meshopt filter: " + request.filter());
    }

    private static MeshoptDecodeResult decodeOctahedralAttributes(MeshoptDecodeRequest request) {
        if (request.mode() != MeshoptCompressionMode.ATTRIBUTES) {
            throw new IllegalArgumentException(
                "OCTAHEDRAL filter is only supported for ATTRIBUTES mode, got: " + request.mode());
        }
        if (request.byteStride() < 4) {
            throw new IllegalArgumentException(
                "OCTAHEDRAL decode requires SNORM16x2 source stride >= 4, got: " + request.byteStride());
        }
        long required = (long) request.count() * (long) request.byteStride();
        byte[] payload = ensureScratch(request.decompressedSize());
        Lz4BlockDecompressor.decompressInto(request.compressedPayload(), payload);

        if (required > request.decompressedSize()) {
            throw new IllegalArgumentException(
                "Decoded payload too small for count/stride: required=" + required + " actual=" + request.decompressedSize());
        }

        byte[] out = new byte[request.count() * 12];
        Vector3f tmp = OCTA_TMP.get();
        for (int i = 0; i < request.count(); i++) {
            int base = i * request.byteStride();
            int lo = u16le(payload, base);
            int hi = u16le(payload, base + 2);
            int packed = lo | (hi << 16);
            OctaNormal.decodeSnorm16(packed, tmp);

            int outBase = i * 12;
            putF32le(out, outBase, tmp.x());
            putF32le(out, outBase + 4, tmp.y());
            putF32le(out, outBase + 8, tmp.z());
        }
        return new MeshoptDecodeResult(out, request.count(), 12);
    }

    private static byte[] ensureScratch(int minLength) {
        byte[] scratch = DECOMPRESS_SCRATCH.get();
        if (scratch.length >= minLength) {
            return scratch;
        }
        byte[] grown = new byte[minLength];
        DECOMPRESS_SCRATCH.set(grown);
        return grown;
    }

    private static int u16le(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static void putF32le(byte[] out, int offset, float value) {
        int bits = Float.floatToRawIntBits(value);
        out[offset] = (byte) bits;
        out[offset + 1] = (byte) (bits >>> 8);
        out[offset + 2] = (byte) (bits >>> 16);
        out[offset + 3] = (byte) (bits >>> 24);
    }
}
