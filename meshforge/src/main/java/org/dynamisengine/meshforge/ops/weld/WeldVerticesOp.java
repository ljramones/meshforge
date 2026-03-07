package org.dynamisengine.meshforge.ops.weld;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.ops.optimize.CompactVerticesOp;
import org.dynamisengine.meshforge.ops.pipeline.MeshContext;
import org.dynamisengine.meshforge.ops.pipeline.MeshOp;

import java.util.Arrays;

/**
 * Boundary-safe weld using union-find with 13-direction forward neighbor checks.
 *
 * Performance note: this intentionally costs more than a single-bucket weld.
 * The extra neighbor-cell comparisons are required for correctness: vertices
 * within epsilon that straddle quantization cell boundaries must still weld.
 * The old single-bucket approach missed those cases and could produce invalid
 * topology on real multi-material assets.
 *
 * Baseline (current): ~7.3 ms on RevitHouse-scale fixture in JMH weld bench.
 * Working arrays are thread-local and reused across calls to keep allocation
 * near-zero in steady state.
 */
public final class WeldVerticesOp implements MeshOp {
    private static final int[][] FORWARD_NEIGHBOR_OFFSETS = new int[][] {
        {1, 0, 0},
        {1, 1, 0},
        {1, -1, 0},
        {1, 0, 1},
        {1, 0, -1},
        {1, 1, 1},
        {1, 1, -1},
        {1, -1, 1},
        {1, -1, -1},
        {0, 1, 0},
        {0, 1, 1},
        {0, 1, -1},
        {0, 0, 1}
    };

    private final float epsilon;
    private static final ThreadLocal<WeldWorkBuffers> WORK_BUFFERS =
        ThreadLocal.withInitial(WeldWorkBuffers::new);

    /**
     * Creates a new {@code WeldVerticesOp} instance.
     * @param epsilon parameter value
     */
    public WeldVerticesOp(float epsilon) {
        if (epsilon <= 0.0f) {
            throw new IllegalArgumentException("epsilon must be > 0");
        }
        this.epsilon = epsilon;
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
        if (indices == null || indices.length == 0) {
            return mesh;
        }

        VertexAttributeView position = require(mesh, AttributeSemantic.POSITION, 0);
        float[] pos = requireFloat(position, "POSITION");
        int posComps = position.format().components();
        if (posComps < 3) {
            throw new IllegalStateException("POSITION[0] must have at least 3 components");
        }

        int vertexCount = mesh.vertexCount();
        float epsilonSquared = epsilon * epsilon;

        int tableCapacity = tableCapacityFor(vertexCount * 2);
        WeldWorkBuffers work = WORK_BUFFERS.get();
        work.ensure(vertexCount, indices.length, tableCapacity);
        work.reset(vertexCount, indices.length, tableCapacity);

        long[] tableHashes = work.tableHashes;
        int[] tableCellIndex = work.tableCellIndex;
        int mask = tableCapacity - 1;

        int[] cellQx = work.cellQx;
        int[] cellQy = work.cellQy;
        int[] cellQz = work.cellQz;
        int[] cellHead = work.cellHead;
        float[] cellMinX = work.cellMinX;
        float[] cellMinY = work.cellMinY;
        float[] cellMinZ = work.cellMinZ;
        float[] cellMaxX = work.cellMaxX;
        float[] cellMaxY = work.cellMaxY;
        float[] cellMaxZ = work.cellMaxZ;
        int[] vertexNext = work.vertexNext;

        int cellCount = 0;
        for (int v = 0; v < vertexCount; v++) {
            int p = v * posComps;
            float vx = pos[p];
            float vy = pos[p + 1];
            float vz = pos[p + 2];
            int qx = quantize(vx, epsilon);
            int qy = quantize(vy, epsilon);
            int qz = quantize(vz, epsilon);

            long hash = hashCell(qx, qy, qz);
            int cell = getOrInsertCell(
                qx, qy, qz, hash, tableHashes, tableCellIndex, mask,
                cellQx, cellQy, cellQz, cellCount
            );
            if (cell == cellCount) {
                cellCount++;
            }

            vertexNext[v] = cellHead[cell];
            cellHead[cell] = v;

            if (vx < cellMinX[cell]) {
                cellMinX[cell] = vx;
            }
            if (vy < cellMinY[cell]) {
                cellMinY[cell] = vy;
            }
            if (vz < cellMinZ[cell]) {
                cellMinZ[cell] = vz;
            }
            if (vx > cellMaxX[cell]) {
                cellMaxX[cell] = vx;
            }
            if (vy > cellMaxY[cell]) {
                cellMaxY[cell] = vy;
            }
            if (vz > cellMaxZ[cell]) {
                cellMaxZ[cell] = vz;
            }
        }

        int[] parent = work.parent;
        byte[] rank = work.rank;
        for (int i = 0; i < vertexCount; i++) {
            parent[i] = i;
        }

        for (int cell = 0; cell < cellCount; cell++) {
            mergeWithinCell(cellHead[cell], vertexNext, pos, posComps, epsilonSquared, parent, rank);
        }

        for (int cell = 0; cell < cellCount; cell++) {
            int qx = cellQx[cell];
            int qy = cellQy[cell];
            int qz = cellQz[cell];
            for (int[] offset : FORWARD_NEIGHBOR_OFFSETS) {
                int neighbor = findCell(
                    qx + offset[0],
                    qy + offset[1],
                    qz + offset[2],
                    tableHashes, tableCellIndex, mask,
                    cellQx, cellQy, cellQz
                );
                if (neighbor < 0) {
                    continue;
                }
                if (minDistanceSquaredBetweenBounds(cell, neighbor, cellMinX, cellMinY, cellMinZ, cellMaxX, cellMaxY, cellMaxZ) > epsilonSquared) {
                    continue;
                }
                mergeAcrossCells(cellHead[cell], cellHead[neighbor], vertexNext, pos, posComps, epsilonSquared, parent, rank);
            }
        }

        int[] remapped = work.remap;
        boolean changed = false;
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            int root = find(parent, idx);
            remapped[i] = root;
            if (root != idx) {
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

    private static int tableCapacityFor(int minEntries) {
        int cap = 16;
        while (cap < minEntries && cap > 0) {
            cap <<= 1;
        }
        if (cap <= 0) {
            throw new IllegalStateException("Weld table capacity overflow");
        }
        return cap;
    }

    private static int getOrInsertCell(
        int qx,
        int qy,
        int qz,
        long hash,
        long[] tableHashes,
        int[] tableCellIndex,
        int mask,
        int[] cellQx,
        int[] cellQy,
        int[] cellQz,
        int nextCell
    ) {
        int slot = mixToIndex(hash, mask);
        while (true) {
            int cell = tableCellIndex[slot];
            if (cell == -1) {
                tableCellIndex[slot] = nextCell;
                tableHashes[slot] = hash;
                cellQx[nextCell] = qx;
                cellQy[nextCell] = qy;
                cellQz[nextCell] = qz;
                return nextCell;
            }
            if (tableHashes[slot] == hash &&
                cellQx[cell] == qx &&
                cellQy[cell] == qy &&
                cellQz[cell] == qz) {
                return cell;
            }
            slot = (slot + 1) & mask;
        }
    }

    private static int findCell(
        int qx,
        int qy,
        int qz,
        long[] tableHashes,
        int[] tableCellIndex,
        int mask,
        int[] cellQx,
        int[] cellQy,
        int[] cellQz
    ) {
        long hash = hashCell(qx, qy, qz);
        int slot = mixToIndex(hash, mask);
        while (true) {
            int cell = tableCellIndex[slot];
            if (cell == -1) {
                return -1;
            }
            if (tableHashes[slot] == hash &&
                cellQx[cell] == qx &&
                cellQy[cell] == qy &&
                cellQz[cell] == qz) {
                return cell;
            }
            slot = (slot + 1) & mask;
        }
    }

    private static void mergeWithinCell(
        int head,
        int[] next,
        float[] positions,
        int posComps,
        float epsilonSquared,
        int[] parent,
        byte[] rank
    ) {
        for (int i = head; i != -1; i = next[i]) {
            int pi = i * posComps;
            float ix = positions[pi];
            float iy = positions[pi + 1];
            float iz = positions[pi + 2];
            for (int j = next[i]; j != -1; j = next[j]) {
                int pj = j * posComps;
                float dx = ix - positions[pj];
                float dy = iy - positions[pj + 1];
                float dz = iz - positions[pj + 2];
                if ((dx * dx + dy * dy + dz * dz) <= epsilonSquared) {
                    union(parent, rank, i, j);
                }
            }
        }
    }

    private static void mergeAcrossCells(
        int headA,
        int headB,
        int[] next,
        float[] positions,
        int posComps,
        float epsilonSquared,
        int[] parent,
        byte[] rank
    ) {
        for (int a = headA; a != -1; a = next[a]) {
            int pa = a * posComps;
            float ax = positions[pa];
            float ay = positions[pa + 1];
            float az = positions[pa + 2];
            for (int b = headB; b != -1; b = next[b]) {
                int pb = b * posComps;
                float dx = ax - positions[pb];
                float dy = ay - positions[pb + 1];
                float dz = az - positions[pb + 2];
                if ((dx * dx + dy * dy + dz * dz) <= epsilonSquared) {
                    union(parent, rank, a, b);
                }
            }
        }
    }

    private static float minDistanceSquaredBetweenBounds(
        int a,
        int b,
        float[] minX,
        float[] minY,
        float[] minZ,
        float[] maxX,
        float[] maxY,
        float[] maxZ
    ) {
        float dx = minAxisDistanceBetweenIntervals(minX[a], maxX[a], minX[b], maxX[b]);
        float dy = minAxisDistanceBetweenIntervals(minY[a], maxY[a], minY[b], maxY[b]);
        float dz = minAxisDistanceBetweenIntervals(minZ[a], maxZ[a], minZ[b], maxZ[b]);
        return dx * dx + dy * dy + dz * dz;
    }

    private static float minAxisDistanceBetweenIntervals(float aMin, float aMax, float bMin, float bMax) {
        if (aMax < bMin) {
            return bMin - aMax;
        }
        if (bMax < aMin) {
            return aMin - bMax;
        }
        return 0.0f;
    }

    private static int find(int[] parent, int x) {
        int root = x;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[x] != x) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    private static void union(int[] parent, byte[] rank, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA == rootB) {
            return;
        }

        byte rankA = rank[rootA];
        byte rankB = rank[rootB];
        if (rankA < rankB || (rankA == rankB && rootA > rootB)) {
            parent[rootA] = rootB;
            return;
        }
        parent[rootB] = rootA;
        if (rankA == rankB) {
            rank[rootA]++;
        }
    }

    private static int quantize(float value, float epsilon) {
        return (int) Math.floor(value / epsilon);
    }

    private static long hashCell(int x, int y, int z) {
        long h = 0x9e3779b97f4a7c15L;
        h ^= x;
        h *= 0x100000001b3L;
        h ^= y;
        h *= 0x100000001b3L;
        h ^= z;
        h *= 0x100000001b3L;
        return h;
    }

    private static int mixToIndex(long hash, int mask) {
        hash ^= (hash >>> 33);
        hash *= 0xff51afd7ed558ccdL;
        hash ^= (hash >>> 33);
        hash *= 0xc4ceb9fe1a85ec53L;
        hash ^= (hash >>> 33);
        return ((int) hash) & mask;
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

    private static final class WeldWorkBuffers {
        long[] tableHashes;
        int[] tableCellIndex;
        int[] cellQx;
        int[] cellQy;
        int[] cellQz;
        int[] cellHead;
        float[] cellMinX;
        float[] cellMinY;
        float[] cellMinZ;
        float[] cellMaxX;
        float[] cellMaxY;
        float[] cellMaxZ;
        int[] vertexNext;
        int[] parent;
        byte[] rank;
        int[] remap;

        void ensure(int vertexCount, int indexCount, int tableCapacity) {
            if (tableHashes == null || tableHashes.length < tableCapacity) {
                tableHashes = new long[tableCapacity];
                tableCellIndex = new int[tableCapacity];
            }
            if (cellHead == null || cellHead.length < vertexCount) {
                cellQx = new int[vertexCount];
                cellQy = new int[vertexCount];
                cellQz = new int[vertexCount];
                cellHead = new int[vertexCount];
                cellMinX = new float[vertexCount];
                cellMinY = new float[vertexCount];
                cellMinZ = new float[vertexCount];
                cellMaxX = new float[vertexCount];
                cellMaxY = new float[vertexCount];
                cellMaxZ = new float[vertexCount];
                vertexNext = new int[vertexCount];
                parent = new int[vertexCount];
                rank = new byte[vertexCount];
            }
            if (remap == null || remap.length < indexCount) {
                remap = new int[indexCount];
            }
        }

        void reset(int vertexCount, int indexCount, int tableCapacity) {
            Arrays.fill(tableCellIndex, 0, tableCapacity, -1);
            Arrays.fill(cellHead, 0, vertexCount, -1);
            Arrays.fill(cellMinX, 0, vertexCount, Float.POSITIVE_INFINITY);
            Arrays.fill(cellMinY, 0, vertexCount, Float.POSITIVE_INFINITY);
            Arrays.fill(cellMinZ, 0, vertexCount, Float.POSITIVE_INFINITY);
            Arrays.fill(cellMaxX, 0, vertexCount, Float.NEGATIVE_INFINITY);
            Arrays.fill(cellMaxY, 0, vertexCount, Float.NEGATIVE_INFINITY);
            Arrays.fill(cellMaxZ, 0, vertexCount, Float.NEGATIVE_INFINITY);
            Arrays.fill(rank, 0, vertexCount, (byte) 0);
            Arrays.fill(remap, 0, indexCount, 0);
        }
    }
}
