package org.dynamisengine.meshforge.mgi;

import java.util.List;

/**
 * Minimal static mesh payload for MGI v1 geometry round-trip.
 *
 * @param positions xyz float triplets (length = vertexCount * 3)
 * @param normalsOrNull optional xyz normal triplets (length = vertexCount * 3)
 * @param uv0OrNull optional uv pairs (length = vertexCount * 2)
 * @param boundsOrNull optional prebaked bounds payload
 * @param indices triangle index buffer (uint32 domain)
 * @param submeshes submesh index ranges
 */
public record MgiStaticMesh(
    float[] positions,
    float[] normalsOrNull,
    float[] uv0OrNull,
    MgiAabb boundsOrNull,
    int[] indices,
    List<MgiSubmeshRange> submeshes
) {
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

        int vertexCount = positions.length / 3;
        if (normalsOrNull != null && normalsOrNull.length != positions.length) {
            throw new IllegalArgumentException("normals length must match positions length");
        }
        if (uv0OrNull != null && uv0OrNull.length != vertexCount * 2) {
            throw new IllegalArgumentException("uv0 length must be vertexCount * 2");
        }

        positions = positions.clone();
        normalsOrNull = normalsOrNull == null ? null : normalsOrNull.clone();
        uv0OrNull = uv0OrNull == null ? null : uv0OrNull.clone();
        boundsOrNull = boundsOrNull == null ? null : new MgiAabb(
            boundsOrNull.minX(),
            boundsOrNull.minY(),
            boundsOrNull.minZ(),
            boundsOrNull.maxX(),
            boundsOrNull.maxY(),
            boundsOrNull.maxZ()
        );
        indices = indices.clone();
        submeshes = List.copyOf(submeshes);
    }

    public int vertexCount() {
        return positions.length / 3;
    }
}
