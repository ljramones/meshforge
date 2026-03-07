package org.dynamisengine.meshforge.ops.optimize;

import org.dynamisengine.meshforge.core.bounds.Aabbf;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.pack.buffer.Meshlet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic CPU meshlet clustering helpers.
 */
public final class MeshletClusters {
    private MeshletClusters() {
    }

    /**
     * Executes reorderIndicesByMeshlets.
     * @param indices parameter value
     * @param maxVerts parameter value
     * @param maxTris parameter value
     * @return resulting value
     */
    public static int[] reorderIndicesByMeshlets(int[] indices, int maxVerts, int maxTris) {
        if (indices == null || indices.length == 0) {
            return indices == null ? null : new int[0];
        }
        if ((indices.length % 3) != 0) {
            throw new IllegalStateException("Triangle index buffer length must be divisible by 3");
        }

        int triCount = indices.length / 3;
        boolean[] used = new boolean[triCount];
        int usedCount = 0;
        int[] out = new int[indices.length];
        int outPos = 0;

        while (usedCount < triCount) {
            int seed = nextUnused(used);
            int[] localVerts = new int[maxVerts];
            int localVertCount = 0;
            int trisInCluster = 0;

            int current = seed;
            while (current >= 0 && trisInCluster < maxTris) {
                int base = current * 3;
                int a = indices[base];
                int b = indices[base + 1];
                int c = indices[base + 2];
                int added = addedVertices(localVerts, localVertCount, a, b, c);
                if (localVertCount + added > maxVerts) {
                    if (trisInCluster == 0) {
                        // Always allow one triangle per meshlet even if limits are tiny.
                    } else {
                        break;
                    }
                }

                used[current] = true;
                usedCount++;
                out[outPos++] = a;
                out[outPos++] = b;
                out[outPos++] = c;
                localVertCount = appendUnique(localVerts, localVertCount, a);
                localVertCount = appendUnique(localVerts, localVertCount, b);
                localVertCount = appendUnique(localVerts, localVertCount, c);
                trisInCluster++;

                if (trisInCluster >= maxTris) {
                    break;
                }
                current = bestNext(indices, used, localVerts, localVertCount, maxVerts);
            }
        }

        return out;
    }

    /**
     * Creates buildMeshlets.
     * @param mesh parameter value
     * @param indices parameter value
     * @param maxVerts parameter value
     * @param maxTris parameter value
     * @return resulting value
     */
    public static List<Meshlet> buildMeshlets(MeshData mesh, int[] indices, int maxVerts, int maxTris) {
        if (indices == null || indices.length == 0) {
            return List.of();
        }
        if ((indices.length % 3) != 0) {
            throw new IllegalStateException("Triangle index buffer length must be divisible by 3");
        }

        float[] pos = mesh.attribute(org.dynamisengine.meshforge.core.attr.AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        if (pos == null) {
            throw new IllegalStateException("POSITION[0] must be float-backed");
        }

        List<Meshlet> out = new ArrayList<>();
        int[] localVerts = new int[maxVerts];
        int[] clusterTriStarts = new int[maxTris];
        int localVertCount = 0;
        int clusterTris = 0;
        int firstTri = 0;
        int triCount = indices.length / 3;

        for (int t = 0; t < triCount; t++) {
            int triBase = t * 3;
            int a = indices[triBase];
            int b = indices[triBase + 1];
            int c = indices[triBase + 2];
            int add = addedVertices(localVerts, localVertCount, a, b, c);
            boolean overflowVerts = (localVertCount + add) > maxVerts;
            boolean overflowTris = clusterTris >= maxTris;
            if ((overflowVerts || overflowTris) && clusterTris > 0) {
                out.add(buildMeshletFromCluster(firstTri, clusterTriStarts, clusterTris, indices, localVerts, localVertCount, pos));
                firstTri = t;
                localVertCount = 0;
                clusterTris = 0;
                Arrays.fill(localVerts, -1);
            }
            clusterTriStarts[clusterTris++] = triBase;
            localVertCount = appendUnique(localVerts, localVertCount, a);
            localVertCount = appendUnique(localVerts, localVertCount, b);
            localVertCount = appendUnique(localVerts, localVertCount, c);
        }
        if (clusterTris > 0) {
            out.add(buildMeshletFromCluster(firstTri, clusterTriStarts, clusterTris, indices, localVerts, localVertCount, pos));
        }
        return out;
    }

    /**
     * Executes reorderIndicesByMeshletMorton.
     * @param mesh parameter value
     * @param indices parameter value
     * @param maxVerts parameter value
     * @param maxTris parameter value
     * @return resulting value
     */
    public static int[] reorderIndicesByMeshletMorton(MeshData mesh, int[] indices, int maxVerts, int maxTris) {
        if (indices == null || indices.length == 0) {
            return indices == null ? null : new int[0];
        }
        if ((indices.length % 3) != 0) {
            throw new IllegalStateException("Triangle index buffer length must be divisible by 3");
        }

        List<Meshlet> meshlets = buildMeshlets(mesh, indices, maxVerts, maxTris);
        if (meshlets.size() <= 1) {
            return indices.clone();
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (Meshlet m : meshlets) {
            Aabbf b = m.bounds();
            if (b.minX() < minX) minX = b.minX();
            if (b.minY() < minY) minY = b.minY();
            if (b.minZ() < minZ) minZ = b.minZ();
            if (b.maxX() > maxX) maxX = b.maxX();
            if (b.maxY() > maxY) maxY = b.maxY();
            if (b.maxZ() > maxZ) maxZ = b.maxZ();
        }

        List<ScoredMeshlet> scored = new ArrayList<>(meshlets.size());
        for (Meshlet m : meshlets) {
            Aabbf b = m.bounds();
            float cx = (b.minX() + b.maxX()) * 0.5f;
            float cy = (b.minY() + b.maxY()) * 0.5f;
            float cz = (b.minZ() + b.maxZ()) * 0.5f;
            int qx = quantize21(cx, minX, maxX);
            int qy = quantize21(cy, minY, maxY);
            int qz = quantize21(cz, minZ, maxZ);
            long morton = morton3d21(qx, qy, qz);
            scored.add(new ScoredMeshlet(m, morton));
        }
        scored.sort((a, b) -> {
            int c = Long.compareUnsigned(a.morton, b.morton);
            if (c != 0) return c;
            return Integer.compare(a.meshlet.firstTriangle(), b.meshlet.firstTriangle());
        });

        int[] out = new int[indices.length];
        int outPos = 0;
        for (ScoredMeshlet s : scored) {
            int first = s.meshlet.firstIndex();
            int count = s.meshlet.indexCount();
            System.arraycopy(indices, first, out, outPos, count);
            outPos += count;
        }
        return out;
    }

    private static Meshlet buildMeshletFromCluster(
        int firstTri,
        int[] clusterTriStarts,
        int clusterTris,
        int[] indices,
        int[] localVerts,
        int localVertCount,
        float[] pos
    ) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < localVertCount; i++) {
            int v = localVerts[i];
            int p = v * 3;
            float x = pos[p];
            float y = pos[p + 1];
            float z = pos[p + 2];
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }

        float axisX = 0.0f;
        float axisY = 0.0f;
        float axisZ = 1.0f;
        float cutoff = -1.0f;
        if (clusterTris > 0) {
            float sx = 0.0f;
            float sy = 0.0f;
            float sz = 0.0f;
            for (int i = 0; i < clusterTris; i++) {
                int triBase = clusterTriStarts[i];
                int ia = indices[triBase] * 3;
                int ib = indices[triBase + 1] * 3;
                int ic = indices[triBase + 2] * 3;

                float ax = pos[ia], ay = pos[ia + 1], az = pos[ia + 2];
                float bx = pos[ib], by = pos[ib + 1], bz = pos[ib + 2];
                float cx = pos[ic], cy = pos[ic + 1], cz = pos[ic + 2];
                float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
                float e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
                float nx = e1y * e2z - e1z * e2y;
                float ny = e1z * e2x - e1x * e2z;
                float nz = e1x * e2y - e1y * e2x;
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1.0e-12f) {
                    sx += nx / len;
                    sy += ny / len;
                    sz += nz / len;
                }
            }
            float alen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (alen > 1.0e-12f) {
                axisX = sx / alen;
                axisY = sy / alen;
                axisZ = sz / alen;
                cutoff = 1.0f;
                for (int i = 0; i < clusterTris; i++) {
                    int triBase = clusterTriStarts[i];
                    int ia = indices[triBase] * 3;
                    int ib = indices[triBase + 1] * 3;
                    int ic = indices[triBase + 2] * 3;
                    float ax = pos[ia], ay = pos[ia + 1], az = pos[ia + 2];
                    float bx = pos[ib], by = pos[ib + 1], bz = pos[ib + 2];
                    float cx = pos[ic], cy = pos[ic + 1], cz = pos[ic + 2];
                    float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
                    float e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
                    float nx = e1y * e2z - e1z * e2y;
                    float ny = e1z * e2x - e1x * e2z;
                    float nz = e1x * e2y - e1y * e2x;
                    float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (len > 1.0e-12f) {
                        float d = (axisX * (nx / len)) + (axisY * (ny / len)) + (axisZ * (nz / len));
                        if (d < cutoff) {
                            cutoff = d;
                        }
                    }
                }
            }
        }

        int firstIndex = firstTri * 3;
        int indexCount = clusterTris * 3;
        return new Meshlet(
            firstTri,
            clusterTris,
            firstIndex,
            indexCount,
            localVertCount,
            new Aabbf(minX, minY, minZ, maxX, maxY, maxZ),
            axisX,
            axisY,
            axisZ,
            cutoff
        );
    }

    private static int nextUnused(boolean[] used) {
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) {
                return i;
            }
        }
        return -1;
    }

    private static int bestNext(int[] indices, boolean[] used, int[] localVerts, int localVertCount, int maxVerts) {
        int bestTri = -1;
        int bestScore = -1;
        int triCount = indices.length / 3;
        for (int t = 0; t < triCount; t++) {
            if (used[t]) {
                continue;
            }
            int base = t * 3;
            int a = indices[base];
            int b = indices[base + 1];
            int c = indices[base + 2];
            int add = addedVertices(localVerts, localVertCount, a, b, c);
            if ((localVertCount + add) > maxVerts) {
                continue;
            }
            int score = sharedVertices(localVerts, localVertCount, a, b, c);
            if (score > bestScore) {
                bestScore = score;
                bestTri = t;
            }
        }
        return bestTri;
    }

    private static int sharedVertices(int[] localVerts, int localVertCount, int a, int b, int c) {
        int score = 0;
        if (contains(localVerts, localVertCount, a)) score++;
        if (contains(localVerts, localVertCount, b)) score++;
        if (contains(localVerts, localVertCount, c)) score++;
        return score;
    }

    private static int addedVertices(int[] localVerts, int localVertCount, int a, int b, int c) {
        int add = 0;
        if (!contains(localVerts, localVertCount, a)) add++;
        if (!contains(localVerts, localVertCount, b) && b != a) add++;
        if (!contains(localVerts, localVertCount, c) && c != a && c != b) add++;
        return add;
    }

    private static boolean contains(int[] localVerts, int localVertCount, int value) {
        for (int i = 0; i < localVertCount; i++) {
            if (localVerts[i] == value) {
                return true;
            }
        }
        return false;
    }

    private static int appendUnique(int[] localVerts, int localVertCount, int value) {
        if (contains(localVerts, localVertCount, value)) {
            return localVertCount;
        }
        localVerts[localVertCount] = value;
        return localVertCount + 1;
    }

    private static int quantize21(float value, float min, float max) {
        if (max <= min) {
            return 0;
        }
        float t = (value - min) / (max - min);
        if (t < 0.0f) t = 0.0f;
        if (t > 1.0f) t = 1.0f;
        return Math.min(0x1FFFFF, Math.max(0, Math.round(t * 0x1FFFFF)));
    }

    private static long morton3d21(int x, int y, int z) {
        long xx = splitBy2(x);
        long yy = splitBy2(y) << 1;
        long zz = splitBy2(z) << 2;
        return xx | yy | zz;
    }

    private static long splitBy2(int v) {
        long x = v & 0x1FFFFF;
        x = (x | (x << 32)) & 0x1F00000000FFFFL;
        x = (x | (x << 16)) & 0x1F0000FF0000FFL;
        x = (x | (x << 8)) & 0x100F00F00F00F00FL;
        x = (x | (x << 4)) & 0x10C30C30C30C30C3L;
        x = (x | (x << 2)) & 0x1249249249249249L;
        return x;
    }

    /**
     * Executes averageMeshletCenterStep.
     * @param meshlets parameter value
     * @return resulting value
     */
    public static double averageMeshletCenterStep(List<Meshlet> meshlets) {
        if (meshlets == null || meshlets.size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        int steps = 0;
        float px = 0.0f, py = 0.0f, pz = 0.0f;
        boolean hasPrev = false;
        for (Meshlet m : meshlets) {
            Aabbf b = m.bounds();
            float cx = (b.minX() + b.maxX()) * 0.5f;
            float cy = (b.minY() + b.maxY()) * 0.5f;
            float cz = (b.minZ() + b.maxZ()) * 0.5f;
            if (hasPrev) {
                double dx = cx - px;
                double dy = cy - py;
                double dz = cz - pz;
                sum += Math.sqrt(dx * dx + dy * dy + dz * dz);
                steps++;
            }
            px = cx;
            py = cy;
            pz = cz;
            hasPrev = true;
        }
        return steps == 0 ? 0.0 : (sum / steps);
    }

    private record ScoredMeshlet(Meshlet meshlet, long morton) {
    }
}
