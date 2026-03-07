package org.dynamisengine.meshforge.ops.pipeline;

import org.dynamisengine.meshforge.core.mesh.MeshData;

/**
 * Deterministic composition utility for executing {@link MeshOp} sequences.
 */
public final class MeshPipeline {
    private MeshPipeline() {
    }

    /**
     * Applies operations left-to-right, feeding each result into the next op.
     * Null op entries are skipped.
     *
     * @param mesh source mesh
     * @param ops operation sequence
     * @return processed mesh
     */
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
