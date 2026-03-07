package org.dynamisengine.meshforge.api;

import org.dynamisengine.meshforge.ops.optimize.OptimizeVertexCacheOp;
import org.dynamisengine.meshforge.ops.optimize.ClusterizeMeshletsOp;
import org.dynamisengine.meshforge.ops.optimize.OptimizeMeshletOrderOp;
import org.dynamisengine.meshforge.ops.generate.ComputeBoundsOp;
import org.dynamisengine.meshforge.ops.generate.RecalculateNormalsOp;
import org.dynamisengine.meshforge.ops.generate.RecalculateTangentsOp;
import org.dynamisengine.meshforge.ops.generate.RemoveDegeneratesOp;
import org.dynamisengine.meshforge.ops.modify.EnsureTrianglesOp;
import org.dynamisengine.meshforge.ops.optimize.CompactVerticesOp;
import org.dynamisengine.meshforge.ops.pipeline.MeshOp;
import org.dynamisengine.meshforge.ops.pipeline.ValidateOp;
import org.dynamisengine.meshforge.ops.weld.WeldVerticesOp;

/**
 * Entry point for mesh operation composition helpers.
 */
public final class Ops {

    private Ops() {
    }

    /**
     * Executes optimizeVertexCache.
     * @return resulting value
     */
    public static MeshOp optimizeVertexCache() {
        return new OptimizeVertexCacheOp();
    }

    /**
     * Executes clusterizeMeshlets.
     * @param maxVertices parameter value
     * @param maxTriangles parameter value
     * @return resulting value
     */
    public static MeshOp clusterizeMeshlets(int maxVertices, int maxTriangles) {
        return new ClusterizeMeshletsOp(maxVertices, maxTriangles);
    }

    /**
     * Executes optimizeMeshletOrder.
     * @param maxVertices parameter value
     * @param maxTriangles parameter value
     * @return resulting value
     */
    public static MeshOp optimizeMeshletOrder(int maxVertices, int maxTriangles) {
        return new OptimizeMeshletOrderOp(maxVertices, maxTriangles);
    }

    /**
     * Executes optimizeMeshletOrder.
     * @return resulting value
     */
    public static MeshOp optimizeMeshletOrder() {
        return optimizeMeshletOrder(128, 64);
    }

    /**
     * Executes bounds.
     * @return resulting value
     */
    public static MeshOp bounds() {
        return new ComputeBoundsOp();
    }

    /**
     * Executes normals.
     * @param angleThresholdDeg parameter value
     * @return resulting value
     */
    public static MeshOp normals(float angleThresholdDeg) {
        return new RecalculateNormalsOp(angleThresholdDeg);
    }

    /**
     * Executes tangents.
     * @return resulting value
     */
    public static MeshOp tangents() {
        return new RecalculateTangentsOp();
    }

    /**
     * Executes validate.
     * @return resulting value
     */
    public static MeshOp validate() {
        return new ValidateOp();
    }

    /**
     * Removes removeDegenerates.
     * @return resulting value
     */
    public static MeshOp removeDegenerates() {
        return new RemoveDegeneratesOp();
    }

    /**
     * Executes weld.
     * @param epsilon parameter value
     * @return resulting value
     */
    public static MeshOp weld(float epsilon) {
        return new WeldVerticesOp(epsilon);
    }

    /**
     * Executes compactVertices.
     * @return resulting value
     */
    public static MeshOp compactVertices() {
        return new CompactVerticesOp();
    }

    /**
     * Executes ensureTriangles.
     * @return resulting value
     */
    public static MeshOp ensureTriangles() {
        return new EnsureTrianglesOp();
    }

    /**
     * v1 alias for triangle normalization.
     * Full polygon triangulation modes are planned for a later model extension.
     *
     * @return triangle-normalization operation
     */
    public static MeshOp triangulate() {
        return new EnsureTrianglesOp();
    }
}
