package org.meshforge.ops.generate;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;
import org.vectrix.core.Vector3f;

import java.util.Arrays;

public final class RecalculateTangentsOp implements MeshOp {
    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        VertexAttributeView position = require(mesh, AttributeSemantic.POSITION, 0);
        VertexAttributeView normal = require(mesh, AttributeSemantic.NORMAL, 0);
        VertexAttributeView uv = require(mesh, AttributeSemantic.UV, 0);

        float[] pos = requireFloat(position, "POSITION");
        float[] nrm = requireFloat(normal, "NORMAL");
        float[] uvData = requireFloat(uv, "UV");

        int posComps = position.format().components();
        int nrmComps = normal.format().components();
        int uvComps = uv.format().components();

        if (posComps < 3 || nrmComps < 3 || uvComps < 2) {
            throw new IllegalStateException("POSITION/NORMAL/UV components are insufficient");
        }

        VertexAttributeView tangent;
        if (mesh.has(AttributeSemantic.TANGENT, 0)) {
            tangent = mesh.attribute(AttributeSemantic.TANGENT, 0);
        } else {
            tangent = mesh.addAttribute(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4);
        }
        float[] tanOut = requireFloat(tangent, "TANGENT");

        int vcount = mesh.vertexCount();
        float[] tan1 = new float[vcount * 3];
        float[] tan2 = new float[vcount * 3];
        Arrays.fill(tanOut, 0.0f);

        int[] indices = mesh.indicesOrNull();
        if (indices == null) {
            int triCount = vcount / 3;
            for (int t = 0; t < triCount; t++) {
                accumulate(t * 3, t * 3 + 1, t * 3 + 2, pos, posComps, uvData, uvComps, tan1, tan2);
            }
        } else {
            int triCount = indices.length / 3;
            for (int t = 0; t < triCount; t++) {
                int i0 = indices[t * 3];
                int i1 = indices[t * 3 + 1];
                int i2 = indices[t * 3 + 2];
                accumulate(i0, i1, i2, pos, posComps, uvData, uvComps, tan1, tan2);
            }
        }

        Vector3f n = new Vector3f();
        Vector3f t = new Vector3f();
        Vector3f c = new Vector3f();

        for (int i = 0; i < vcount; i++) {
            int no = i * nrmComps;
            int to = i * 3;
            int out = i * 4;

            n.set(nrm[no], nrm[no + 1], nrm[no + 2]);
            t.set(tan1[to], tan1[to + 1], tan1[to + 2]);

            float ndott = n.dot(t);
            t.set(
                t.x() - n.x() * ndott,
                t.y() - n.y() * ndott,
                t.z() - n.z() * ndott
            );
            if (t.lengthSquared() > 1.0e-20f) {
                t.normalize();
            } else {
                t.set(1.0f, 0.0f, 0.0f);
            }

            c.set(n).cross(t);
            float tan2x = tan2[to];
            float tan2y = tan2[to + 1];
            float tan2z = tan2[to + 2];
            float handedness = c.x() * tan2x + c.y() * tan2y + c.z() * tan2z < 0.0f ? -1.0f : 1.0f;

            tanOut[out] = t.x;
            tanOut[out + 1] = t.y;
            tanOut[out + 2] = t.z;
            tanOut[out + 3] = handedness;
        }

        return mesh;
    }

    private static void accumulate(
        int i0,
        int i1,
        int i2,
        float[] pos,
        int posComps,
        float[] uv,
        int uvComps,
        float[] tan1,
        float[] tan2
    ) {
        int p0 = i0 * posComps;
        int p1 = i1 * posComps;
        int p2 = i2 * posComps;

        float x1 = pos[p1] - pos[p0];
        float y1 = pos[p1 + 1] - pos[p0 + 1];
        float z1 = pos[p1 + 2] - pos[p0 + 2];

        float x2 = pos[p2] - pos[p0];
        float y2 = pos[p2 + 1] - pos[p0 + 1];
        float z2 = pos[p2 + 2] - pos[p0 + 2];

        int w0 = i0 * uvComps;
        int w1 = i1 * uvComps;
        int w2 = i2 * uvComps;

        float s1 = uv[w1] - uv[w0];
        float t1 = uv[w1 + 1] - uv[w0 + 1];
        float s2 = uv[w2] - uv[w0];
        float t2 = uv[w2 + 1] - uv[w0 + 1];

        float denom = s1 * t2 - s2 * t1;
        if (Math.abs(denom) < 1.0e-20f) {
            return;
        }
        float r = 1.0f / denom;

        float sx = (t2 * x1 - t1 * x2) * r;
        float sy = (t2 * y1 - t1 * y2) * r;
        float sz = (t2 * z1 - t1 * z2) * r;

        float tx = (s1 * x2 - s2 * x1) * r;
        float ty = (s1 * y2 - s2 * y1) * r;
        float tz = (s1 * z2 - s2 * z1) * r;

        add3(tan1, i0, sx, sy, sz);
        add3(tan1, i1, sx, sy, sz);
        add3(tan1, i2, sx, sy, sz);

        add3(tan2, i0, tx, ty, tz);
        add3(tan2, i1, tx, ty, tz);
        add3(tan2, i2, tx, ty, tz);
    }

    private static void add3(float[] arr, int index, float x, float y, float z) {
        int o = index * 3;
        arr[o] += x;
        arr[o + 1] += y;
        arr[o + 2] += z;
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
}
