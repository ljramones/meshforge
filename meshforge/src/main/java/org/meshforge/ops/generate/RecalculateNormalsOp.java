package org.meshforge.ops.generate;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;
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
            float clampedAngle = Math.max(0.0f, Math.min(180.0f, angleThresholdDeg));
            if (clampedAngle >= 179.999f) {
                accumulateIndexed(indices, positions, posComps, normalData, normalComps);
            } else {
                accumulateIndexedWithThreshold(
                    indices,
                    mesh.vertexCount(),
                    positions,
                    posComps,
                    normalData,
                    normalComps,
                    clampedAngle
                );
            }
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

    private static void accumulateIndexedWithThreshold(
        int[] indices,
        int vertexCount,
        float[] positions,
        int posComps,
        float[] normalData,
        int normalComps,
        float angleThresholdDeg
    ) {
        int triCount = indices.length / 3;
        float[] faceNx = new float[triCount];
        float[] faceNy = new float[triCount];
        float[] faceNz = new float[triCount];
        int[] incidentCounts = new int[vertexCount];

        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3];
            int b = indices[t * 3 + 1];
            int c = indices[t * 3 + 2];
            int ao = a * posComps;
            int bo = b * posComps;
            int co = c * posComps;

            float ax = positions[ao];
            float ay = positions[ao + 1];
            float az = positions[ao + 2];
            float bx = positions[bo];
            float by = positions[bo + 1];
            float bz = positions[bo + 2];
            float cx = positions[co];
            float cy = positions[co + 1];
            float cz = positions[co + 2];

            float e1x = bx - ax;
            float e1y = by - ay;
            float e1z = bz - az;
            float e2x = cx - ax;
            float e2y = cy - ay;
            float e2z = cz - az;

            faceNx[t] = e1y * e2z - e1z * e2y;
            faceNy[t] = e1z * e2x - e1x * e2z;
            faceNz[t] = e1x * e2y - e1y * e2x;

            incidentCounts[a]++;
            incidentCounts[b]++;
            incidentCounts[c]++;
        }

        int[] offsets = new int[vertexCount + 1];
        for (int v = 0; v < vertexCount; v++) {
            offsets[v + 1] = offsets[v] + incidentCounts[v];
        }
        int[] cursor = offsets.clone();
        int[] adjacency = new int[offsets[vertexCount]];
        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3];
            int b = indices[t * 3 + 1];
            int c = indices[t * 3 + 2];
            adjacency[cursor[a]++] = t;
            adjacency[cursor[b]++] = t;
            adjacency[cursor[c]++] = t;
        }

        float cosThreshold = (float) Math.cos(Math.toRadians(angleThresholdDeg));
        for (int v = 0; v < vertexCount; v++) {
            int start = offsets[v];
            int end = offsets[v + 1];
            if (start >= end) {
                continue;
            }

            float bestX = 0.0f;
            float bestY = 0.0f;
            float bestZ = 0.0f;
            float bestLenSq = -1.0f;

            for (int s = start; s < end; s++) {
                int seedFace = adjacency[s];
                float sx = faceNx[seedFace];
                float sy = faceNy[seedFace];
                float sz = faceNz[seedFace];
                float seedLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
                if (seedLen <= 1.0e-20f) {
                    continue;
                }

                float sumX = 0.0f;
                float sumY = 0.0f;
                float sumZ = 0.0f;
                for (int i = start; i < end; i++) {
                    int face = adjacency[i];
                    float fx = faceNx[face];
                    float fy = faceNy[face];
                    float fz = faceNz[face];
                    float len = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
                    if (len <= 1.0e-20f) {
                        continue;
                    }
                    float dot = (sx * fx + sy * fy + sz * fz) / (seedLen * len);
                    if (dot >= cosThreshold) {
                        sumX += fx;
                        sumY += fy;
                        sumZ += fz;
                    }
                }

                float lenSq = sumX * sumX + sumY * sumY + sumZ * sumZ;
                if (lenSq > bestLenSq) {
                    bestLenSq = lenSq;
                    bestX = sumX;
                    bestY = sumY;
                    bestZ = sumZ;
                }
            }

            if (bestLenSq > 1.0e-20f) {
                accumulate(normalData, normalComps, v, bestX, bestY, bestZ);
            }
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

        float e1x = bx - ax;
        float e1y = by - ay;
        float e1z = bz - az;
        float e2x = cx - ax;
        float e2y = cy - ay;
        float e2z = cz - az;

        float nx = e1y * e2z - e1z * e2y;
        float ny = e1z * e2x - e1x * e2z;
        float nz = e1x * e2y - e1y * e2x;

        accumulate(normalData, normalComps, ia, nx, ny, nz);
        accumulate(normalData, normalComps, ib, nx, ny, nz);
        accumulate(normalData, normalComps, ic, nx, ny, nz);
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
