package org.meshforge.ops.pipeline;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;

public final class ValidateOp implements MeshOp {
    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        if (!mesh.has(AttributeSemantic.POSITION, 0)) {
            throw new IllegalStateException("Mesh is missing required POSITION[0] attribute");
        }

        int[] indices = mesh.indicesOrNull();
        int vertexCount = mesh.vertexCount();

        if (mesh.topology() == Topology.TRIANGLES && indices != null && (indices.length % 3) != 0) {
            throw new IllegalStateException("Triangle index buffer length must be divisible by 3");
        }

        if (indices != null) {
            for (int i = 0; i < indices.length; i++) {
                int idx = indices[i];
                if (idx < 0 || idx >= vertexCount) {
                    throw new IllegalStateException("Index out of range at " + i + ": " + idx);
                }
            }
        }

        int indexCount = indices == null ? 0 : indices.length;
        for (Submesh submesh : mesh.submeshes()) {
            if (submesh.firstIndex() < 0) {
                throw new IllegalStateException("Submesh firstIndex must be >= 0");
            }
            if (submesh.indexCount() < 0) {
                throw new IllegalStateException("Submesh indexCount must be >= 0");
            }
            long end = (long) submesh.firstIndex() + submesh.indexCount();
            if (indices != null && end > indexCount) {
                throw new IllegalStateException("Submesh range exceeds index buffer");
            }
        }

        return mesh;
    }
}
