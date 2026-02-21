package org.meshforge.api;

import org.meshforge.ops.optimize.OptimizeVertexCacheOp;
import org.meshforge.ops.generate.ComputeBoundsOp;
import org.meshforge.ops.generate.RecalculateNormalsOp;
import org.meshforge.ops.generate.RecalculateTangentsOp;
import org.meshforge.ops.generate.RemoveDegeneratesOp;
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
}
