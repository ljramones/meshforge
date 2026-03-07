package org.dynamisengine.meshforge.core.mesh;

/**
 * Index range and optional material binding for one logical submesh.
 *
 * @param firstIndex first index in the parent index buffer
 * @param indexCount number of indices in this submesh range
 * @param materialId optional material identifier associated with the range
 */
public record Submesh(int firstIndex, int indexCount, Object materialId) {
}
