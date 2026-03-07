package org.dynamisengine.meshforge.pack.buffer;

import java.util.List;

/**
 * Immutable meshlet collection view for renderer upload and culling logic.
 */
public interface MeshletBufferView {
    /**
     * Returns the number of meshlets.
     *
     * @return meshlet count
     */
    int meshletCount();

    /**
     * Returns one meshlet by index.
     *
     * @param index meshlet index
     * @return meshlet descriptor
     */
    Meshlet meshlet(int index);

    /**
     * Returns all meshlets as an immutable list.
     *
     * @return meshlet list
     */
    List<Meshlet> asList();

    /**
     * Wraps a list as a {@link MeshletBufferView}.
     *
     * @param meshlets meshlet list
     * @return meshlet view
     */
    static MeshletBufferView of(List<Meshlet> meshlets) {
        return new SimpleMeshletBufferView(meshlets);
    }
}
