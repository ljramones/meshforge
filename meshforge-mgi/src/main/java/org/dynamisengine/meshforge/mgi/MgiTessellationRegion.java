package org.dynamisengine.meshforge.mgi;

/**
 * One tessellation/subdivision-relevant geometry region in MGI metadata.
 *
 * @param submeshIndex source submesh index
 * @param firstIndex inclusive first index in parent index buffer
 * @param indexCount index count for this region
 * @param patchControlPoints patch control-point count for this region
 * @param tessLevel tessellation level prepared offline
 * @param flags tessellation behavior flags
 */
public record MgiTessellationRegion(
    int submeshIndex,
    int firstIndex,
    int indexCount,
    int patchControlPoints,
    float tessLevel,
    int flags
) {
    public MgiTessellationRegion {
        if (submeshIndex < 0) {
            throw new IllegalArgumentException("submeshIndex must be >= 0");
        }
        if (firstIndex < 0) {
            throw new IllegalArgumentException("firstIndex must be >= 0");
        }
        if (indexCount <= 0) {
            throw new IllegalArgumentException("indexCount must be > 0");
        }
        if (patchControlPoints < 3) {
            throw new IllegalArgumentException("patchControlPoints must be >= 3");
        }
        if (!Float.isFinite(tessLevel) || tessLevel <= 0f) {
            throw new IllegalArgumentException("tessLevel must be finite and > 0");
        }
        if (flags < 0) {
            throw new IllegalArgumentException("flags must be >= 0");
        }
    }
}

