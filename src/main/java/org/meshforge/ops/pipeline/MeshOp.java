package org.meshforge.ops.pipeline;

import org.meshforge.core.mesh.MeshData;

@FunctionalInterface
public interface MeshOp {
    MeshData apply(MeshData mesh, MeshContext context);
}
