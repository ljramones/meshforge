package org.dynamisengine.meshforge.pack.buffer;

import org.dynamisengine.meshforge.core.bounds.Aabbf;

/**
 * Immutable meshlet descriptor for clustered triangle ranges.
 *
 * @param firstTriangle first triangle index in the meshlet triangle stream
 * @param triangleCount number of triangles in this meshlet
 * @param firstIndex first index into the meshlet local index stream
 * @param indexCount number of local indices for this meshlet
 * @param uniqueVertexCount number of unique vertices referenced by the meshlet
 * @param bounds meshlet-local axis-aligned bounds
 * @param coneAxisX cone axis X component
 * @param coneAxisY cone axis Y component
 * @param coneAxisZ cone axis Z component
 * @param coneCutoffCos cone cutoff cosine for backface culling
 */
public record Meshlet(
    int firstTriangle,
    int triangleCount,
    int firstIndex,
    int indexCount,
    int uniqueVertexCount,
    Aabbf bounds,
    float coneAxisX,
    float coneAxisY,
    float coneAxisZ,
    float coneCutoffCos
) {
}
