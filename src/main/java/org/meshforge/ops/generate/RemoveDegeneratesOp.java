package org.meshforge.ops.generate;

import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;

import java.util.Arrays;

/**
 * Removes degenerate indexed triangles where vertices repeat.
 */
public final class RemoveDegeneratesOp implements MeshOp {
    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length < 3) {
            return mesh;
        }

        int triCount = indices.length / 3;
        int[] out = new int[indices.length];
        int write = 0;

        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3];
            int b = indices[t * 3 + 1];
            int c = indices[t * 3 + 2];
            if (a == b || b == c || a == c) {
                continue;
            }
            out[write++] = a;
            out[write++] = b;
            out[write++] = c;
        }

        if (write != indices.length) {
            mesh.setIndices(Arrays.copyOf(out, write));
        }
        return mesh;
    }
}
