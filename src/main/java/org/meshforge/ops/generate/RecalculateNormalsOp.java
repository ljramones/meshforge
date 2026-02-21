package org.meshforge.ops.generate;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;
import org.vectrix.core.Vector3f;

import java.util.Arrays;

public final class RecalculateNormalsOp implements MeshOp {
    private final float angleThresholdDeg;

    public RecalculateNormalsOp(float angleThresholdDeg) {
        this.angleThresholdDeg = angleThresholdDeg;
    }

    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        VertexAttributeView position = require(mesh, AttributeSemantic.POSITION, 0);
        float[] positions = requireFloat(position, "POSITION");
        int posComps = position.format().components();
        if (posComps < 3) {
            throw new IllegalStateException("POSITION[0] must have at least 3 components");
        }

        VertexAttributeView normals;
        if (mesh.has(AttributeSemantic.NORMAL, 0)) {
            normals = mesh.attribute(AttributeSemantic.NORMAL, 0);
        } else {
            normals = mesh.addAttribute(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3);
        }

        float[] normalData = requireFloat(normals, "NORMAL");
        int normalComps = normals.format().components();
        if (normalComps < 3) {
            throw new IllegalStateException("NORMAL[0] must have at least 3 components");
        }
        Arrays.fill(normalData, 0.0f);

        int[] indices = mesh.indicesOrNull();
        if (indices == null) {
            accumulateSequential(mesh.vertexCount(), positions, posComps, normalData, normalComps);
        } else {
            accumulateIndexed(indices, positions, posComps, normalData, normalComps);
        }

        normalize(normalData, mesh.vertexCount(), normalComps);
        return mesh;
    }

    private static void accumulateSequential(
        int vertexCount,
        float[] positions,
        int posComps,
        float[] normalData,
        int normalComps
    ) {
        int triCount = vertexCount / 3;
        for (int t = 0; t < triCount; t++) {
            int a = t * 3;
            int b = a + 1;
            int c = a + 2;
            accumulateTriangle(a, b, c, positions, posComps, normalData, normalComps);
        }
    }

    private static void accumulateIndexed(
        int[] indices,
        float[] positions,
        int posComps,
        float[] normalData,
        int normalComps
    ) {
        int triCount = indices.length / 3;
        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3];
            int b = indices[t * 3 + 1];
            int c = indices[t * 3 + 2];
            accumulateTriangle(a, b, c, positions, posComps, normalData, normalComps);
        }
    }

    private static void accumulateTriangle(
        int ia,
        int ib,
        int ic,
        float[] positions,
        int posComps,
        float[] normalData,
        int normalComps
    ) {
        int a = ia * posComps;
        int b = ib * posComps;
        int c = ic * posComps;

        float ax = positions[a];
        float ay = positions[a + 1];
        float az = positions[a + 2];

        float bx = positions[b];
        float by = positions[b + 1];
        float bz = positions[b + 2];

        float cx = positions[c];
        float cy = positions[c + 1];
        float cz = positions[c + 2];

        Vector3f e1 = new Vector3f(bx - ax, by - ay, bz - az);
        Vector3f e2 = new Vector3f(cx - ax, cy - ay, cz - az);
        Vector3f n = e1.cross(e2, new Vector3f());

        accumulate(normalData, normalComps, ia, n.x, n.y, n.z);
        accumulate(normalData, normalComps, ib, n.x, n.y, n.z);
        accumulate(normalData, normalComps, ic, n.x, n.y, n.z);
    }

    private static void accumulate(float[] normalData, int normalComps, int vertex, float x, float y, float z) {
        int n = vertex * normalComps;
        normalData[n] += x;
        normalData[n + 1] += y;
        normalData[n + 2] += z;
    }

    private static void normalize(float[] normalData, int vertexCount, int normalComps) {
        for (int i = 0; i < vertexCount; i++) {
            int n = i * normalComps;
            float x = normalData[n];
            float y = normalData[n + 1];
            float z = normalData[n + 2];
            float lenSq = x * x + y * y + z * z;
            if (lenSq > 1.0e-20f) {
                float inv = (float) (1.0 / Math.sqrt(lenSq));
                normalData[n] = x * inv;
                normalData[n + 1] = y * inv;
                normalData[n + 2] = z * inv;
            } else {
                normalData[n] = 0.0f;
                normalData[n + 1] = 1.0f;
                normalData[n + 2] = 0.0f;
            }
        }
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

    public float angleThresholdDeg() {
        return angleThresholdDeg;
    }
}
