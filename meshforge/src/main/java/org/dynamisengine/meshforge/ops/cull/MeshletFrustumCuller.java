package org.dynamisengine.meshforge.ops.cull;

import org.dynamisengine.meshforge.core.bounds.Aabbf;

import java.util.List;

/**
 * CPU meshlet frustum culling helpers.
 */
public final class MeshletFrustumCuller {
    private MeshletFrustumCuller() {
    }

    /**
     * Builds a compact list of visible meshlet indices.
     *
     * @param meshletBounds meshlet bounds payload
     * @param frustum frustum to test against
     * @return visible meshlet indices
     */
    public static int[] buildVisibleIndexList(List<Aabbf> meshletBounds, ViewFrustum frustum) {
        if (meshletBounds == null) {
            throw new NullPointerException("meshletBounds");
        }
        if (frustum == null) {
            throw new NullPointerException("frustum");
        }

        int[] temp = new int[meshletBounds.size()];
        int count = 0;
        for (int i = 0; i < meshletBounds.size(); i++) {
            Aabbf bounds = meshletBounds.get(i);
            if (bounds == null) {
                throw new NullPointerException("meshletBounds[" + i + "]");
            }
            if (frustum.intersects(bounds)) {
                temp[count++] = i;
            }
        }

        int[] out = new int[count];
        System.arraycopy(temp, 0, out, 0, count);
        return out;
    }

    /**
     * Produces summary stats for one culling pass.
     *
     * @param meshletBounds meshlet bounds
     * @param meshletTriangleCounts triangle count per meshlet
     * @param frustum frustum to test against
     * @return culling stats
     */
    public static CullingStats cull(List<Aabbf> meshletBounds, int[] meshletTriangleCounts, ViewFrustum frustum) {
        Validation validation = validateInputs(meshletBounds, meshletTriangleCounts, frustum);
        int totalTriangles = validation.totalTriangles;
        int[] visible = buildVisibleIndexList(meshletBounds, frustum);
        int visibleTriangles = 0;
        for (int index : visible) {
            visibleTriangles += meshletTriangleCounts[index];
        }

        double reduction = totalTriangles == 0
            ? 0.0
            : (1.0 - (visibleTriangles / (double) totalTriangles)) * 100.0;

        return new CullingStats(
            meshletBounds.size(),
            visible.length,
            totalTriangles,
            visibleTriangles,
            reduction,
            visible
        );
    }

    /**
     * Allocation-free culling summary for hot-path timing/benchmark use.
     *
     * @param meshletBounds meshlet bounds
     * @param meshletTriangleCounts triangle count per meshlet
     * @param frustum frustum to test against
     * @return summary stats without visible index materialization
     */
    public static CullingSummary cullSummary(List<Aabbf> meshletBounds, int[] meshletTriangleCounts, ViewFrustum frustum) {
        Validation validation = validateInputs(meshletBounds, meshletTriangleCounts, frustum);
        int totalTriangles = validation.totalTriangles;
        int visibleMeshlets = 0;
        int visibleTriangles = 0;

        for (int i = 0; i < meshletBounds.size(); i++) {
            Aabbf bounds = meshletBounds.get(i);
            if (bounds == null) {
                throw new NullPointerException("meshletBounds[" + i + "]");
            }
            if (frustum.intersects(bounds)) {
                visibleMeshlets++;
                visibleTriangles += meshletTriangleCounts[i];
            }
        }

        double reduction = totalTriangles == 0
            ? 0.0
            : (1.0 - (visibleTriangles / (double) totalTriangles)) * 100.0;

        return new CullingSummary(
            meshletBounds.size(),
            visibleMeshlets,
            totalTriangles,
            visibleTriangles,
            reduction
        );
    }

    private static Validation validateInputs(List<Aabbf> meshletBounds, int[] meshletTriangleCounts, ViewFrustum frustum) {
        if (meshletBounds == null) {
            throw new NullPointerException("meshletBounds");
        }
        if (meshletTriangleCounts == null) {
            throw new NullPointerException("meshletTriangleCounts");
        }
        if (frustum == null) {
            throw new NullPointerException("frustum");
        }
        if (meshletBounds.size() != meshletTriangleCounts.length) {
            throw new IllegalArgumentException("meshletBounds size must match meshletTriangleCounts length");
        }
        int totalTriangles = 0;
        for (int triangles : meshletTriangleCounts) {
            if (triangles < 0) {
                throw new IllegalArgumentException("meshletTriangleCounts must be >= 0");
            }
            totalTriangles += triangles;
        }
        return new Validation(totalTriangles);
    }

    public record CullingStats(
        int totalMeshlets,
        int visibleMeshlets,
        int totalTriangles,
        int visibleTriangles,
        double triangleReductionPercent,
        int[] visibleIndices
    ) {
        public CullingStats {
            visibleIndices = visibleIndices == null ? null : visibleIndices.clone();
        }
    }

    public record CullingSummary(
        int totalMeshlets,
        int visibleMeshlets,
        int totalTriangles,
        int visibleTriangles,
        double triangleReductionPercent
    ) {
    }

    private record Validation(int totalTriangles) {
    }
}
