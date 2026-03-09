package org.dynamisengine.meshforge.mgi;

/**
 * Prebaked axis-aligned bounds payload for MGI meshes.
 *
 * @param minX minimum x
 * @param minY minimum y
 * @param minZ minimum z
 * @param maxX maximum x
 * @param maxY maximum y
 * @param maxZ maximum z
 */
public record MgiAabb(
    float minX,
    float minY,
    float minZ,
    float maxX,
    float maxY,
    float maxZ
) {
    public MgiAabb {
        if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(minZ)
            || !Float.isFinite(maxX) || !Float.isFinite(maxY) || !Float.isFinite(maxZ)) {
            throw new IllegalArgumentException("bounds values must be finite");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("invalid bounds extents (min > max)");
        }
    }
}
