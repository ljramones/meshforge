package org.dynamisengine.meshforge.loader.gltf.read;

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.gpu.OctaNormal;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    private MeshoptDecoder() {
    }

    /**
     * Executes decode.
     * @param request parameter value
     * @return resulting value
     */
    public static MeshoptDecodeResult decode(MeshoptDecodeRequest request) {
        byte[] decompressed = Lz4BlockDecompressor.decompress(
            request.compressedPayload(),
            request.decompressedSize());

        if (request.filter() == MeshoptCompressionFilter.NONE) {
            return new MeshoptDecodeResult(decompressed, request.count(), request.byteStride());
        }

        if (request.filter() == MeshoptCompressionFilter.OCTAHEDRAL) {
            return decodeOctahedralAttributes(request, decompressed);
        }

        throw new IllegalArgumentException("Unsupported meshopt filter: " + request.filter());
    }

    private static MeshoptDecodeResult decodeOctahedralAttributes(MeshoptDecodeRequest request, byte[] payload) {
        if (request.mode() != MeshoptCompressionMode.ATTRIBUTES) {
            throw new IllegalArgumentException(
                "OCTAHEDRAL filter is only supported for ATTRIBUTES mode, got: " + request.mode());
        }
        if (request.byteStride() < 4) {
            throw new IllegalArgumentException(
                "OCTAHEDRAL decode requires SNORM16x2 source stride >= 4, got: " + request.byteStride());
        }
        long required = (long) request.count() * (long) request.byteStride();
        if (required > payload.length) {
            throw new IllegalArgumentException(
                "Decoded payload too small for count/stride: required=" + required + " actual=" + payload.length);
        }

        byte[] out = new byte[request.count() * 12];
        ByteBuffer src = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer dst = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        Vector3f tmp = new Vector3f();
        for (int i = 0; i < request.count(); i++) {
            int base = i * request.byteStride();
            int lo = src.getShort(base) & 0xFFFF;
            int hi = src.getShort(base + 2) & 0xFFFF;
            int packed = lo | (hi << 16);
            OctaNormal.decodeSnorm16(packed, tmp);
            dst.putFloat(tmp.x());
            dst.putFloat(tmp.y());
            dst.putFloat(tmp.z());
        }
        return new MeshoptDecodeResult(out, request.count(), 12);
    }
}
