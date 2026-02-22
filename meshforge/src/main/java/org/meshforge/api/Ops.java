package org.meshforge.api;

import org.meshforge.ops.optimize.OptimizeVertexCacheOp;
import org.meshforge.ops.optimize.ClusterizeMeshletsOp;
import org.meshforge.ops.optimize.OptimizeMeshletOrderOp;
import org.meshforge.ops.generate.ComputeBoundsOp;
import org.meshforge.ops.generate.RecalculateNormalsOp;
import org.meshforge.ops.generate.RecalculateTangentsOp;
import org.meshforge.ops.generate.RemoveDegeneratesOp;
import org.meshforge.ops.modify.EnsureTrianglesOp;
import org.meshforge.ops.optimize.CompactVerticesOp;
import org.meshforge.ops.pipeline.MeshOp;
import org.meshforge.ops.pipeline.ValidateOp;
import org.meshforge.ops.weld.WeldVerticesOp;

/**
 * Entry point for mesh operation composition helpers.
 */
public final class Ops {

    private Ops() {
    }

    public static MeshOp optimizeVertexCache() {
        return new OptimizeVertexCacheOp();
    }

    public static MeshOp clusterizeMeshlets(int maxVertices, int maxTriangles) {
        return new ClusterizeMeshletsOp(maxVertices, maxTriangles);
    }

    public static MeshOp optimizeMeshletOrder(int maxVertices, int maxTriangles) {
        return new OptimizeMeshletOrderOp(maxVertices, maxTriangles);
    }

    public static MeshOp optimizeMeshletOrder() {
        return optimizeMeshletOrder(128, 64);
    }

    public static MeshOp bounds() {
        return new ComputeBoundsOp();
    }

    public static MeshOp normals(float angleThresholdDeg) {
        return new RecalculateNormalsOp(angleThresholdDeg);
    }

    public static MeshOp tangents() {
        return new RecalculateTangentsOp();
    }

    public static MeshOp validate() {
        return new ValidateOp();
    }

    public static MeshOp removeDegenerates() {
        return new RemoveDegeneratesOp();
    }

    public static MeshOp weld(float epsilon) {
        return new WeldVerticesOp(epsilon);
    }

    public static MeshOp compactVertices() {
        return new CompactVerticesOp();
    }

    public static MeshOp ensureTriangles() {
        return new EnsureTrianglesOp();
    }

    /**
     * v1 alias for triangle normalization.
     * Full polygon triangulation modes are planned for a later model extension.
     */
    public static MeshOp triangulate() {
        return new EnsureTrianglesOp();
    }
}
