package org.dynamisengine.meshforge.ops.pipeline;

import org.dynamisengine.meshforge.core.mesh.MeshData;

/**
 * Single mesh processing operation in a pipeline chain.
 */
@FunctionalInterface
public interface MeshOp {
    /**
     * Applies this operation to a mesh.
     *
     * @param mesh input mesh
     * @param context shared pipeline context
     * @return transformed mesh
     */
    MeshData apply(MeshData mesh, MeshContext context);
}
