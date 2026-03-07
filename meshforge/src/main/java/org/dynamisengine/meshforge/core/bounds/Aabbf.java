package org.dynamisengine.meshforge.core.bounds;

/**
 * Axis-aligned bounding box in float precision.
 *
 * @param minX minimum X
 * @param minY minimum Y
 * @param minZ minimum Z
 * @param maxX maximum X
 * @param maxY maximum Y
 * @param maxZ maximum Z
 */
public record Aabbf(
    float minX,
    float minY,
    float minZ,
    float maxX,
    float maxY,
    float maxZ
) {
}
