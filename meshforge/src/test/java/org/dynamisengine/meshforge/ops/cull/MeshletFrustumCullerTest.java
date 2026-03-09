package org.dynamisengine.meshforge.ops.cull;

import org.dynamisengine.meshforge.core.bounds.Aabbf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshletFrustumCullerTest {

    @Test
    void frustumIntersectsInsideAndRejectsOutsideBounds() {
        ViewFrustum frustum = ViewFrustum.fromAabbWindow(new Aabbf(0f, 0f, 0f, 10f, 10f, 10f));

        assertTrue(frustum.intersects(new Aabbf(2f, 2f, 2f, 3f, 3f, 3f)));
        assertTrue(frustum.intersects(new Aabbf(10f, 5f, 5f, 11f, 6f, 6f)));
        assertFalse(frustum.intersects(new Aabbf(11f, 11f, 11f, 12f, 12f, 12f)));
    }

    @Test
    void buildsVisibleIndexList() {
        List<Aabbf> bounds = List.of(
            new Aabbf(0f, 0f, 0f, 1f, 1f, 1f),
            new Aabbf(8f, 8f, 8f, 9f, 9f, 9f),
            new Aabbf(20f, 20f, 20f, 21f, 21f, 21f)
        );
        ViewFrustum frustum = ViewFrustum.fromAabbWindow(new Aabbf(0f, 0f, 0f, 10f, 10f, 10f));

        int[] visible = MeshletFrustumCuller.buildVisibleIndexList(bounds, frustum);

        assertArrayEquals(new int[] {0, 1}, visible);
    }

    @Test
    void cullsAndComputesTriangleReductionStats() {
        List<Aabbf> bounds = List.of(
            new Aabbf(0f, 0f, 0f, 1f, 1f, 1f),
            new Aabbf(8f, 8f, 8f, 9f, 9f, 9f),
            new Aabbf(20f, 20f, 20f, 21f, 21f, 21f)
        );
        int[] triangleCounts = new int[] {10, 20, 30};
        ViewFrustum frustum = ViewFrustum.fromAabbWindow(new Aabbf(0f, 0f, 0f, 10f, 10f, 10f));

        MeshletFrustumCuller.CullingStats stats = MeshletFrustumCuller.cull(bounds, triangleCounts, frustum);

        assertEquals(3, stats.totalMeshlets());
        assertEquals(2, stats.visibleMeshlets());
        assertEquals(60, stats.totalTriangles());
        assertEquals(30, stats.visibleTriangles());
        assertEquals(50.0, stats.triangleReductionPercent());
        assertArrayEquals(new int[] {0, 1}, stats.visibleIndices());
    }

    @Test
    void allocationFreeSummaryMatchesCullingStats() {
        List<Aabbf> bounds = List.of(
            new Aabbf(0f, 0f, 0f, 1f, 1f, 1f),
            new Aabbf(8f, 8f, 8f, 9f, 9f, 9f),
            new Aabbf(20f, 20f, 20f, 21f, 21f, 21f)
        );
        int[] triangleCounts = new int[] {10, 20, 30};
        ViewFrustum frustum = ViewFrustum.fromAabbWindow(new Aabbf(0f, 0f, 0f, 10f, 10f, 10f));

        MeshletFrustumCuller.CullingStats withIndices = MeshletFrustumCuller.cull(bounds, triangleCounts, frustum);
        MeshletFrustumCuller.CullingSummary summary = MeshletFrustumCuller.cullSummary(bounds, triangleCounts, frustum);

        assertEquals(withIndices.totalMeshlets(), summary.totalMeshlets());
        assertEquals(withIndices.visibleMeshlets(), summary.visibleMeshlets());
        assertEquals(withIndices.totalTriangles(), summary.totalTriangles());
        assertEquals(withIndices.visibleTriangles(), summary.visibleTriangles());
        assertEquals(withIndices.triangleReductionPercent(), summary.triangleReductionPercent());
    }

    @Test
    void rejectsMismatchedTriangleCountsLength() {
        List<Aabbf> bounds = List.of(new Aabbf(0f, 0f, 0f, 1f, 1f, 1f));
        int[] triangleCounts = new int[] {1, 2};
        ViewFrustum frustum = ViewFrustum.fromAabbWindow(new Aabbf(0f, 0f, 0f, 10f, 10f, 10f));

        assertThrows(IllegalArgumentException.class,
            () -> MeshletFrustumCuller.cull(bounds, triangleCounts, frustum));
    }
}
