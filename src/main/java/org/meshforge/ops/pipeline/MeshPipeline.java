package org.meshforge.ops.pipeline;

import org.meshforge.core.mesh.MeshData;

public final class MeshPipeline {
    private MeshPipeline() {
    }

    public static MeshData run(MeshData mesh, MeshOp... ops) {
        MeshContext context = new MeshContext();
        MeshData current = mesh;
        if (ops == null) {
            return current;
        }
        for (MeshOp op : ops) {
            if (op == null) {
                continue;
            }
            current = op.apply(current, context);
        }
        return current;
    }
}
