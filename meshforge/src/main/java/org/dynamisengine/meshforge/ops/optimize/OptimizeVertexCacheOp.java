package org.dynamisengine.meshforge.ops.optimize;

import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.ops.pipeline.MeshContext;
import org.dynamisengine.meshforge.ops.pipeline.MeshOp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Forsyth-style vertex cache optimization for indexed triangle lists.
 *
 * Produces reordered indices and a vertex remap, then applies attribute reorder+compaction.
 */
public final class OptimizeVertexCacheOp implements MeshOp {
    private final int cacheSize;

    /**
     * Creates a new {@code OptimizeVertexCacheOp} instance.
     */
    public OptimizeVertexCacheOp() {
        this(32);
    }

    /**
     * Creates a new {@code OptimizeVertexCacheOp} instance.
     * @param cacheSize parameter value
     */
    public OptimizeVertexCacheOp(int cacheSize) {
        this.cacheSize = Math.max(3, cacheSize);
    }

    @Override
    /**
     * Executes apply.
     * @param mesh parameter value
     * @param context parameter value
     * @return resulting value
     */
    public MeshData apply(MeshData mesh, MeshContext context) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length < 3) {
            return mesh;
        }

        int vertexCount = mesh.vertexCount();
        int[] reorderedOldIndices;
        List<Submesh> remappedSubmeshes = mesh.submeshes();
        List<Submesh> sourceSubmeshes = mesh.submeshes();
        if (sourceSubmeshes.isEmpty()) {
            reorderedOldIndices = optimizeOrder(indices, vertexCount, cacheSize);
        } else {
            reorderedOldIndices = new int[indices.length];
            remappedSubmeshes = new ArrayList<>(sourceSubmeshes.size());
            int write = 0;
            for (Submesh submesh : sourceSubmeshes) {
                int first = submesh.firstIndex();
                int count = submesh.indexCount();
                if (first < 0 || count < 0 || first + count > indices.length) {
                    throw new IllegalStateException("Submesh range exceeds index buffer");
                }
                if ((count % 3) != 0) {
                    throw new IllegalStateException("Submesh indexCount must be divisible by 3");
                }

                int[] slice = Arrays.copyOfRange(indices, first, first + count);
                int[] reorderedSlice = optimizeOrder(slice, vertexCount, cacheSize);
                System.arraycopy(reorderedSlice, 0, reorderedOldIndices, write, reorderedSlice.length);
                remappedSubmeshes.add(new Submesh(write, reorderedSlice.length, submesh.materialId()));
                write += reorderedSlice.length;
            }
        }

        int[] oldToNew = new int[vertexCount];
        Arrays.fill(oldToNew, -1);
        int[] compactedIndices = new int[reorderedOldIndices.length];
        int nextVertex = 0;
        for (int i = 0; i < reorderedOldIndices.length; i++) {
            int oldIndex = reorderedOldIndices[i];
            int mapped = oldToNew[oldIndex];
            if (mapped < 0) {
                mapped = nextVertex++;
                oldToNew[oldIndex] = mapped;
            }
            compactedIndices[i] = mapped;
        }

        context.put("optimizeVertexCache.vertexRemap", oldToNew);
        context.put("optimizeVertexCache.acmrBefore", CacheMetrics.acmr(indices, cacheSize));
        context.put("optimizeVertexCache.acmrAfter", CacheMetrics.acmr(compactedIndices, cacheSize));

        return CompactVerticesOp.reorderAndCompact(
            mesh,
            compactedIndices,
            oldToNew,
            nextVertex,
            remappedSubmeshes
        );
    }

    /**
     * Executes optimizeOrder.
     * @param indices parameter value
     * @param vertexCount parameter value
     * @param cacheSize parameter value
     * @return resulting value
     */
    public static int[] optimizeOrder(int[] indices, int vertexCount, int cacheSize) {
        return optimizeInternal(indices, vertexCount, cacheSize).reorderedIndices();
    }

    /**
     * Executes optimize.
     * @param indices parameter value
     * @param vertexCount parameter value
     * @param cacheSize parameter value
     * @return resulting value
     */
    public static Result optimize(int[] indices, int vertexCount, int cacheSize) {
        InternalResult internal = optimizeInternal(indices, vertexCount, cacheSize);
        int[] output = internal.reorderedIndices();
        int[] oldToNew = new int[vertexCount];
        Arrays.fill(oldToNew, -1);
        int[] compactedIndices = new int[output.length];
        int nextVertex = 0;
        for (int i = 0; i < output.length; i++) {
            int oldIndex = output[i];
            int mapped = oldToNew[oldIndex];
            if (mapped < 0) {
                mapped = nextVertex++;
                oldToNew[oldIndex] = mapped;
            }
            compactedIndices[i] = mapped;
        }

        return new Result(compactedIndices, oldToNew, nextVertex);
    }

    private static InternalResult optimizeInternal(int[] indices, int vertexCount, int cacheSize) {
        if ((indices.length % 3) != 0) {
            throw new IllegalArgumentException("Triangle index buffer length must be divisible by 3");
        }

        int triCount = indices.length / 3;
        if (triCount == 0) {
            return new InternalResult(indices.clone());
        }

        int[] liveTriCount = new int[vertexCount];
        int maxLiveTriCount = 0;
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            if (idx < 0 || idx >= vertexCount) {
                throw new IllegalArgumentException("Index out of range at " + i + ": " + idx);
            }
            liveTriCount[idx]++;
            if (liveTriCount[idx] > maxLiveTriCount) {
                maxLiveTriCount = liveTriCount[idx];
            }
        }

        int[][] adjacency = new int[vertexCount][];
        int[] cursor = new int[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            adjacency[v] = new int[liveTriCount[v]];
        }
        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3];
            int b = indices[t * 3 + 1];
            int c = indices[t * 3 + 2];
            adjacency[a][cursor[a]++] = t;
            adjacency[b][cursor[b]++] = t;
            adjacency[c][cursor[c]++] = t;
        }

        boolean[] emitted = new boolean[triCount];
        int[] triVersion = new int[triCount];
        int[] triSeenStamp = new int[triCount];
        int seenStamp = 1;

        IntFloatMaxHeap heap = new IntFloatMaxHeap(Math.max(16, triCount));
        final float scoreEpsilon = 1.0e-6f;

        int[] cachePos = new int[vertexCount];
        Arrays.fill(cachePos, -1);
        float[] cacheScore = buildCacheScore(cacheSize);
        float[] valenceScore = buildValenceScore(maxLiveTriCount);

        float[] vertexScore = new float[vertexCount];
        for (int v = 0; v < vertexCount; v++) {
            vertexScore[v] = scoreVertexLookup(cachePos[v], liveTriCount[v], cacheScore, valenceScore);
        }

        float[] triangleScore = new float[triCount];
        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3];
            int b = indices[t * 3 + 1];
            int c = indices[t * 3 + 2];
            triangleScore[t] = vertexScore[a] + vertexScore[b] + vertexScore[c];
        }

        int[] output = new int[indices.length];
        int[] lru = new int[cacheSize];
        Arrays.fill(lru, -1);
        int cacheCount = 0;

        int nextSeed = 0;
        int outTri = 0;

        while (outTri < triCount) {
            int bestTri = -1;
            while (!heap.isEmpty()) {
                int tri = heap.peekTri();
                int version = heap.peekVersion();
                heap.pop();
                if (!emitted[tri] && version == triVersion[tri]) {
                    bestTri = tri;
                    break;
                }
            }

            if (bestTri < 0) {
                nextSeed = nextUnemitted(emitted, nextSeed);
                if (nextSeed >= triCount) {
                    break;
                }
                bestTri = nextSeed;
            }

            emitted[bestTri] = true;

            int a = indices[bestTri * 3];
            int b = indices[bestTri * 3 + 1];
            int c = indices[bestTri * 3 + 2];

            output[outTri * 3] = a;
            output[outTri * 3 + 1] = b;
            output[outTri * 3 + 2] = c;
            outTri++;

            cacheCount = touch(lru, cachePos, cacheCount, a);
            cacheCount = touch(lru, cachePos, cacheCount, b);
            cacheCount = touch(lru, cachePos, cacheCount, c);

            liveTriCount[a]--;
            liveTriCount[b]--;
            liveTriCount[c]--;

            if (seenStamp == Integer.MAX_VALUE) {
                Arrays.fill(triSeenStamp, 0);
                seenStamp = 1;
            } else {
                seenStamp++;
            }

            for (int i = 0; i < cacheCount; i++) {
                int v = lru[i];
                vertexScore[v] = scoreVertexLookup(cachePos[v], liveTriCount[v], cacheScore, valenceScore);
                for (int t : adjacency[v]) {
                    if (emitted[t] || triSeenStamp[t] == seenStamp) {
                        continue;
                    }
                    triSeenStamp[t] = seenStamp;
                    int i0 = indices[t * 3];
                    int i1 = indices[t * 3 + 1];
                    int i2 = indices[t * 3 + 2];
                    float updated = vertexScore[i0] + vertexScore[i1] + vertexScore[i2];
                    float previous = triangleScore[t];
                    triangleScore[t] = updated;
                    if (Math.abs(updated - previous) > scoreEpsilon) {
                        int version = ++triVersion[t];
                        heap.push(t, updated, version);
                    }
                }
            }
        }

        return new InternalResult(output);
    }

    private static int nextUnemitted(boolean[] emitted, int from) {
        int i = Math.max(0, from);
        while (i < emitted.length && emitted[i]) {
            i++;
        }
        return i;
    }

    private static int touch(int[] lru, int[] cachePos, int cacheCount, int vertex) {
        int index = cachePos[vertex];
        if (index == 0) {
            return cacheCount;
        }
        if (index > 0) {
            for (int i = index; i > 0; i--) {
                int moved = lru[i - 1];
                lru[i] = moved;
                cachePos[moved] = i;
            }
            lru[0] = vertex;
            cachePos[vertex] = 0;
        } else {
            int shift = Math.min(cacheCount, lru.length - 1);
            if (cacheCount == lru.length) {
                int evicted = lru[lru.length - 1];
                if (evicted >= 0) {
                    cachePos[evicted] = -1;
                }
            }
            for (int i = shift; i > 0; i--) {
                int moved = lru[i - 1];
                lru[i] = moved;
                cachePos[moved] = i;
            }
            lru[0] = vertex;
            cachePos[vertex] = 0;
            if (cacheCount < lru.length) {
                cacheCount++;
            }
        }
        return cacheCount;
    }

    private static float scoreVertexLookup(int cachePos, int liveTris, float[] cacheScore, float[] valenceScore) {
        if (liveTris <= 0) {
            return -1.0f;
        }
        float score = cachePos >= 0 ? cacheScore[cachePos] : 0.0f;
        return score + valenceScore[liveTris];
    }

    private static float[] buildCacheScore(int cacheSize) {
        final float cacheDecayPower = 1.5f;
        final float lastTriScore = 0.75f;
        float[] cacheScore = new float[cacheSize];
        for (int i = 0; i < cacheSize; i++) {
            if (i < 3) {
                cacheScore[i] = lastTriScore;
            } else {
                float scaler = 1.0f / (cacheSize - 3);
                float rank = 1.0f - (i - 3) * scaler;
                cacheScore[i] = (float) Math.pow(rank, cacheDecayPower);
            }
        }
        return cacheScore;
    }

    private static float[] buildValenceScore(int maxLiveTris) {
        final float valenceBoostScale = 2.0f;
        final float valenceBoostPower = 0.5f;
        float[] valenceScore = new float[Math.max(2, maxLiveTris + 1)];
        for (int i = 1; i < valenceScore.length; i++) {
            valenceScore[i] = valenceBoostScale * (float) Math.pow(i, -valenceBoostPower);
        }
        return valenceScore;
    }

    private static final class IntFloatMaxHeap {
        private int[] tri;
        private float[] score;
        private int[] version;
        private int size;

        IntFloatMaxHeap(int initialCapacity) {
            tri = new int[initialCapacity];
            score = new float[initialCapacity];
            version = new int[initialCapacity];
        }

        boolean isEmpty() {
            return size == 0;
        }

        int peekTri() {
            return tri[0];
        }

        int peekVersion() {
            return version[0];
        }

        void push(int t, float s, int v) {
            ensureCapacity(size + 1);
            int i = size++;
            while (i > 0) {
                int p = (i - 1) >>> 1;
                if (score[p] >= s) {
                    break;
                }
                tri[i] = tri[p];
                score[i] = score[p];
                version[i] = version[p];
                i = p;
            }
            tri[i] = t;
            score[i] = s;
            version[i] = v;
        }

        void pop() {
            int last = --size;
            if (last < 0) {
                size = 0;
                return;
            }
            if (last == 0) {
                return;
            }

            int t = tri[last];
            float s = score[last];
            int v = version[last];

            int i = 0;
            int half = last >>> 1;
            while (i < half) {
                int left = (i << 1) + 1;
                int right = left + 1;
                int child = left;
                if (right < last && score[right] > score[left]) {
                    child = right;
                }
                if (score[child] <= s) {
                    break;
                }
                tri[i] = tri[child];
                score[i] = score[child];
                version[i] = version[child];
                i = child;
            }
            tri[i] = t;
            score[i] = s;
            version[i] = v;
        }

        private void ensureCapacity(int needed) {
            if (needed <= tri.length) {
                return;
            }
            int newCap = Math.max(needed, tri.length << 1);
            tri = Arrays.copyOf(tri, newCap);
            score = Arrays.copyOf(score, newCap);
            version = Arrays.copyOf(version, newCap);
        }
    }

    /**
     * Optimization output containing reordered indices and compacted vertex remap data.
     *
     * @param indices reordered and compacted indices
     * @param vertexRemap old-to-new vertex remap table
     * @param vertexCount compacted vertex count
     */
    public record Result(int[] indices, int[] vertexRemap, int vertexCount) {
    }

    private record InternalResult(int[] reorderedIndices) {
    }
}
