package org.dynamisengine.meshforge.loader.gltf.read;

/**
 * Decoded payload and effective output layout.
 */
public record MeshoptDecodeResult(
    byte[] data,
    int count,
    int byteStride
) {
}
