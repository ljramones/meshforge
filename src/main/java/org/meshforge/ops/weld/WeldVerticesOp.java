package org.meshforge.ops.weld;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.optimize.CompactVerticesOp;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;

import java.util.HashMap;
import java.util.Map;

/**
 * Position-based weld using epsilon quantization.
 */
public final class WeldVerticesOp implements MeshOp {
    private final float epsilon;

    public WeldVerticesOp(float epsilon) {
        if (epsilon <= 0.0f) {
            throw new IllegalArgumentException("epsilon must be > 0");
        }
        this.epsilon = epsilon;
    }

    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length == 0) {
            return mesh;
        }

        VertexAttributeView position = require(mesh, AttributeSemantic.POSITION, 0);
        float[] pos = requireFloat(position, "POSITION");
        int posComps = position.format().components();
        if (posComps < 3) {
            throw new IllegalStateException("POSITION[0] must have at least 3 components");
        }

        Map<QuantKey, Integer> reps = new HashMap<>();
        int[] representative = new int[mesh.vertexCount()];

        for (int v = 0; v < mesh.vertexCount(); v++) {
            int p = v * posComps;
            QuantKey key = new QuantKey(
                quantize(pos[p], epsilon),
                quantize(pos[p + 1], epsilon),
                quantize(pos[p + 2], epsilon)
            );
            Integer rep = reps.putIfAbsent(key, v);
            representative[v] = rep == null ? v : rep;
        }

        int[] remapped = new int[indices.length];
        boolean changed = false;
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            int rep = representative[idx];
            remapped[i] = rep;
            if (rep != idx) {
                changed = true;
            }
        }

        if (!changed) {
            return mesh;
        }

        MeshData remappedMesh = CompactVerticesOp.remap(mesh, remapped);
        MeshData compacted = new CompactVerticesOp().apply(remappedMesh, context);
        compacted.setBounds(null);
        return compacted;
    }

    private static int quantize(float value, float epsilon) {
        return Math.round(value / epsilon);
    }

    private static VertexAttributeView require(MeshData mesh, AttributeSemantic semantic, int setIndex) {
        if (!mesh.has(semantic, setIndex)) {
            throw new IllegalStateException("Missing required attribute: " + semantic + "[" + setIndex + "]");
        }
        return mesh.attribute(semantic, setIndex);
    }

    private static float[] requireFloat(VertexAttributeView view, String label) {
        float[] values = view.rawFloatArrayOrNull();
        if (values == null) {
            throw new IllegalStateException(label + " must be float-backed");
        }
        return values;
    }

    private record QuantKey(int x, int y, int z) {
    }
}
