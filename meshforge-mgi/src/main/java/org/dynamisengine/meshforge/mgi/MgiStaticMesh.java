package org.dynamisengine.meshforge.mgi;

import java.util.List;

/**
 * Minimal static mesh payload for MGI v1 geometry round-trip.
 *
 * @param positions xyz float triplets (length = vertexCount * 3)
 * @param indices triangle index buffer (uint32 domain)
 * @param submeshes submesh index ranges
 */
public record MgiStaticMesh(float[] positions, int[] indices, List<MgiSubmeshRange> submeshes) {
    public MgiStaticMesh {
        if (positions == null) {
            throw new NullPointerException("positions");
        }
        if (indices == null) {
            throw new NullPointerException("indices");
        }
        if (submeshes == null) {
            throw new NullPointerException("submeshes");
        }
        if ((positions.length % 3) != 0) {
            throw new IllegalArgumentException("positions length must be divisible by 3");
        }
        positions = positions.clone();
        indices = indices.clone();
        submeshes = List.copyOf(submeshes);
    }

    public int vertexCount() {
        return positions.length / 3;
    }
}
