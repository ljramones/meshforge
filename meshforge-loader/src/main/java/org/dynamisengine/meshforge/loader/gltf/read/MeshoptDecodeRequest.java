package org.dynamisengine.meshforge.loader.gltf.read;

import java.util.Objects;

/**
 * Decode request for a single KHR_meshopt_compression payload.
 */
public record MeshoptDecodeRequest(
    byte[] compressedPayload,
    int decompressedSize,
    int count,
    int byteStride,
    MeshoptCompressionMode mode,
    MeshoptCompressionFilter filter
) {
    public MeshoptDecodeRequest {
        Objects.requireNonNull(compressedPayload, "compressedPayload");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(filter, "filter");
        if (decompressedSize < 0) {
            throw new IllegalArgumentException("decompressedSize must be >= 0");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count must be >= 0");
        }
        if (byteStride <= 0) {
            throw new IllegalArgumentException("byteStride must be > 0");
        }
    }
}
