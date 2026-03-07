package org.dynamisengine.meshforge.ops.optimize;

import java.util.Arrays;

/**
 * Public class CacheMetrics.
 */
public final class CacheMetrics {
    private CacheMetrics() {
    }

    /**
     * Executes acmr.
     * @param indices parameter value
     * @param cacheSize parameter value
     * @return resulting value
     */
    public static double acmr(int[] indices, int cacheSize) {
        if (indices == null || indices.length == 0) {
            return 0.0;
        }
        int misses = cacheMisses(indices, cacheSize);
        int triangles = indices.length / 3;
        return triangles == 0 ? 0.0 : (double) misses / triangles;
    }

    /**
     * Executes atvr.
     * @param indices parameter value
     * @param vertexCount parameter value
     * @param cacheSize parameter value
     * @return resulting value
     */
    public static double atvr(int[] indices, int vertexCount, int cacheSize) {
        if (indices == null || indices.length == 0 || vertexCount <= 0) {
            return 0.0;
        }
        int misses = cacheMisses(indices, cacheSize);
        return (double) misses / vertexCount;
    }

    /**
     * Executes atvr.
     * @param indices parameter value
     * @param cacheSize parameter value
     * @return resulting value
     */
    public static double atvr(int[] indices, int cacheSize) {
        if (indices == null || indices.length == 0) {
            return 0.0;
        }
        int max = -1;
        for (int idx : indices) {
            if (idx > max) {
                max = idx;
            }
        }
        return atvr(indices, max + 1, cacheSize);
    }

    private static int cacheMisses(int[] indices, int cacheSize) {
        int size = Math.max(1, cacheSize);
        int[] lru = new int[size];
        Arrays.fill(lru, -1);

        int misses = 0;
        for (int idx : indices) {
            int pos = -1;
            for (int i = 0; i < size; i++) {
                if (lru[i] == idx) {
                    pos = i;
                    break;
                }
            }
            if (pos >= 0) {
                if (pos > 0) {
                    int hit = lru[pos];
                    System.arraycopy(lru, 0, lru, 1, pos);
                    lru[0] = hit;
                }
            } else {
                misses++;
                System.arraycopy(lru, 0, lru, 1, size - 1);
                lru[0] = idx;
            }
        }
        return misses;
    }
}
