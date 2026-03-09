package org.dynamisengine.meshforge.ops.lod;

/**
 * One meshlet LOD level range and its precomputed geometric error metric.
 *
 * @param lodLevel level index (0 = highest detail)
 * @param meshletStart inclusive meshlet start index
 * @param meshletCount number of meshlets in this level
 * @param geometricError geometric error prepared offline
 */
public record MeshletLodLevelMetadata(
    int lodLevel,
    int meshletStart,
    int meshletCount,
    float geometricError
) {
    public MeshletLodLevelMetadata {
        if (lodLevel < 0) {
            throw new IllegalArgumentException("lodLevel must be >= 0");
        }
        if (meshletStart < 0) {
            throw new IllegalArgumentException("meshletStart must be >= 0");
        }
        if (meshletCount <= 0) {
            throw new IllegalArgumentException("meshletCount must be > 0");
        }
        if (!Float.isFinite(geometricError) || geometricError < 0f) {
            throw new IllegalArgumentException("geometricError must be finite and >= 0");
        }
    }
}
