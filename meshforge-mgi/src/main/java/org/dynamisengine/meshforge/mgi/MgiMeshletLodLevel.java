package org.dynamisengine.meshforge.mgi;

/**
 * One meshlet LOD level range and its precomputed selection metric.
 *
 * @param lodLevel level index (0 = highest detail)
 * @param meshletStart inclusive descriptor index start
 * @param meshletCount number of meshlets in this LOD level
 * @param geometricError geometric error metric prepared offline
 */
public record MgiMeshletLodLevel(
    int lodLevel,
    int meshletStart,
    int meshletCount,
    float geometricError
) {
    public MgiMeshletLodLevel {
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

