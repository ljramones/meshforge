package org.meshforge.ops.optimize;

import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;

import java.util.Arrays;

/**
 * Forsyth-style vertex cache optimization for indexed triangle lists.
 */
public final class OptimizeVertexCacheOp implements MeshOp {
    private final int cacheSize;

    public OptimizeVertexCacheOp() {
        this(32);
    }

    public OptimizeVertexCacheOp(int cacheSize) {
        this.cacheSize = Math.max(3, cacheSize);
    }

    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length < 3) {
            return mesh;
        }

        int triCount = indices.length / 3;
        int vertexCount = mesh.vertexCount();

        int[] liveTriCount = new int[vertexCount];
        for (int i = 0; i < triCount; i++) {
            liveTriCount[indices[i * 3]]++;
            liveTriCount[indices[i * 3 + 1]]++;
            liveTriCount[indices[i * 3 + 2]]++;
        }

        int[][] adjacency = new int[vertexCount][];
        int[] cursor = new int[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            adjacency[v] = new int[liveTriCount[v]];
        }
        for (int i = 0; i < triCount; i++) {
            int a = indices[i * 3];
            int b = indices[i * 3 + 1];
            int c = indices[i * 3 + 2];
            adjacency[a][cursor[a]++] = i;
            adjacency[b][cursor[b]++] = i;
            adjacency[c][cursor[c]++] = i;
        }

        boolean[] emitted = new boolean[triCount];
        int[] cachePos = new int[vertexCount];
        Arrays.fill(cachePos, -1);

        float[] vertexScore = new float[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            vertexScore[v] = scoreVertex(cachePos[v], liveTriCount[v], cacheSize);
        }

        float[] triangleScore = new float[triCount];
        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3];
            int b = indices[t * 3 + 1];
            int c = indices[t * 3 + 2];
            triangleScore[t] = vertexScore[a] + vertexScore[b] + vertexScore[c];
        }

        int[] output = new int[triCount * 3];
        int[] lru = new int[cacheSize];
        Arrays.fill(lru, -1);

        int outTri = 0;
        for (int emittedCount = 0; emittedCount < triCount; emittedCount++) {
            int bestTri = -1;
            float bestScore = -1f;

            for (int i = 0; i < cacheSize; i++) {
                int v = lru[i];
                if (v < 0) {
                    continue;
                }
                for (int t : adjacency[v]) {
                    if (emitted[t]) {
                        continue;
                    }
                    float score = triangleScore[t];
                    if (score > bestScore) {
                        bestScore = score;
                        bestTri = t;
                    }
                }
            }

            if (bestTri < 0) {
                for (int t = 0; t < triCount; t++) {
                    if (emitted[t]) {
                        continue;
                    }
                    float score = triangleScore[t];
                    if (score > bestScore) {
                        bestScore = score;
                        bestTri = t;
                    }
                }
            }

            emitted[bestTri] = true;
            int a = indices[bestTri * 3];
            int b = indices[bestTri * 3 + 1];
            int c = indices[bestTri * 3 + 2];
            output[outTri * 3] = a;
            output[outTri * 3 + 1] = b;
            output[outTri * 3 + 2] = c;
            outTri++;

            touch(lru, a);
            touch(lru, b);
            touch(lru, c);

            Arrays.fill(cachePos, -1);
            for (int i = 0; i < cacheSize; i++) {
                int v = lru[i];
                if (v >= 0) {
                    cachePos[v] = i;
                }
            }

            liveTriCount[a]--;
            liveTriCount[b]--;
            liveTriCount[c]--;

            vertexScore[a] = scoreVertex(cachePos[a], liveTriCount[a], cacheSize);
            vertexScore[b] = scoreVertex(cachePos[b], liveTriCount[b], cacheSize);
            vertexScore[c] = scoreVertex(cachePos[c], liveTriCount[c], cacheSize);

            for (int t : adjacency[a]) {
                if (!emitted[t]) {
                    triangleScore[t] = vertexScore[indices[t * 3]]
                        + vertexScore[indices[t * 3 + 1]]
                        + vertexScore[indices[t * 3 + 2]];
                }
            }
            for (int t : adjacency[b]) {
                if (!emitted[t]) {
                    triangleScore[t] = vertexScore[indices[t * 3]]
                        + vertexScore[indices[t * 3 + 1]]
                        + vertexScore[indices[t * 3 + 2]];
                }
            }
            for (int t : adjacency[c]) {
                if (!emitted[t]) {
                    triangleScore[t] = vertexScore[indices[t * 3]]
                        + vertexScore[indices[t * 3 + 1]]
                        + vertexScore[indices[t * 3 + 2]];
                }
            }
        }

        mesh.setIndices(output);
        return mesh;
    }

    private static void touch(int[] lru, int vertex) {
        int index = -1;
        for (int i = 0; i < lru.length; i++) {
            if (lru[i] == vertex) {
                index = i;
                break;
            }
        }
        if (index == 0) {
            return;
        }
        if (index > 0) {
            int value = lru[index];
            System.arraycopy(lru, 0, lru, 1, index);
            lru[0] = value;
        } else {
            System.arraycopy(lru, 0, lru, 1, lru.length - 1);
            lru[0] = vertex;
        }
    }

    private static float scoreVertex(int cachePos, int liveTris, int cacheSize) {
        final float cacheDecayPower = 1.5f;
        final float lastTriScore = 0.75f;
        final float valenceBoostScale = 2.0f;
        final float valenceBoostPower = 0.5f;

        if (liveTris <= 0) {
            return -1.0f;
        }

        float score = 0.0f;
        if (cachePos < 0) {
            // not in cache
        } else if (cachePos < 3) {
            score = lastTriScore;
        } else {
            float scaler = 1.0f / (cacheSize - 3);
            float rank = 1.0f - (cachePos - 3) * scaler;
            score = (float) Math.pow(rank, cacheDecayPower);
        }

        float valenceBoost = valenceBoostScale * (float) Math.pow(liveTris, -valenceBoostPower);
        return score + valenceBoost;
    }
}
