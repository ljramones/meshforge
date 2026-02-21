package org.meshforge.ops.generate;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;

import java.util.Arrays;

/**
 * Removes degenerate indexed triangles where vertices repeat.
 */
public final class RemoveDegeneratesOp implements MeshOp {
    private static final float AREA_EPSILON = 1.0e-20f;

    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length < 3) {
            return mesh;
        }
        VertexAttributeView position = mesh.has(AttributeSemantic.POSITION, 0)
            ? mesh.attribute(AttributeSemantic.POSITION, 0)
            : null;
        float[] pos = position == null ? null : position.rawFloatArrayOrNull();
        int posComps = position == null ? 0 : position.format().components();

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
            if (pos != null && posComps >= 3 && isZeroArea(a, b, c, pos, posComps)) {
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

    private static boolean isZeroArea(int ia, int ib, int ic, float[] pos, int comps) {
        int a = ia * comps;
        int b = ib * comps;
        int c = ic * comps;

        float ax = pos[a];
        float ay = pos[a + 1];
        float az = pos[a + 2];
        float bx = pos[b];
        float by = pos[b + 1];
        float bz = pos[b + 2];
        float cx = pos[c];
        float cy = pos[c + 1];
        float cz = pos[c + 2];

        float abx = bx - ax;
        float aby = by - ay;
        float abz = bz - az;
        float acx = cx - ax;
        float acy = cy - ay;
        float acz = cz - az;

        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;
        float area2 = nx * nx + ny * ny + nz * nz;
        return area2 <= AREA_EPSILON;
    }
}
