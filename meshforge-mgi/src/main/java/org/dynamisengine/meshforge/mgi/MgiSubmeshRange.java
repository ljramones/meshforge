package org.dynamisengine.meshforge.mgi;

/**
 * Static mesh submesh index range metadata.
 *
 * @param firstIndex first index in the global index buffer
 * @param indexCount number of indices in this submesh
 * @param materialSlot material-slot id (opaque)
 */
public record MgiSubmeshRange(int firstIndex, int indexCount, int materialSlot) {
    public MgiSubmeshRange {
        if (firstIndex < 0) {
            throw new IllegalArgumentException("firstIndex must be >= 0");
        }
        if (indexCount < 0) {
            throw new IllegalArgumentException("indexCount must be >= 0");
        }
        if (materialSlot < 0) {
            throw new IllegalArgumentException("materialSlot must be >= 0");
        }
    }
}
