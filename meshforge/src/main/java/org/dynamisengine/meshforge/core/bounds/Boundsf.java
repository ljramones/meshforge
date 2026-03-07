package org.dynamisengine.meshforge.core.bounds;

/**
 * Combined bounds payload containing AABB and sphere forms.
 *
 * @param aabb axis-aligned bounding box
 * @param sphere bounding sphere
 */
public record Boundsf(Aabbf aabb, Spheref sphere) {
}
