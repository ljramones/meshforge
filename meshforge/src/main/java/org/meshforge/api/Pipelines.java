package org.meshforge.api;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshOp;
import org.meshforge.ops.pipeline.MeshPipeline;

import java.util.ArrayList;
import java.util.List;

/**
 * Preset mesh-processing pipelines.
 */
public final class Pipelines {
    private Pipelines() {
    }

    /**
     * Full realtime prep pipeline.
     * Includes weld and cache optimization for import-time quality/perf.
     */
    public static MeshData realtime(MeshData mesh) {
        return MeshPipeline.run(mesh, realtimeOps(mesh));
    }

    /**
     * Fast realtime prep pipeline.
     * For already-clean meshes: skips weld and cache optimization.
     */
    public static MeshData realtimeFast(MeshData mesh) {
        return MeshPipeline.run(mesh, realtimeFastOps(mesh));
    }

    public static MeshOp[] realtimeOps(MeshData mesh) {
        List<MeshOp> ops = new ArrayList<>();
        ops.add(Ops.validate());
        ops.add(Ops.removeDegenerates());
        ops.add(Ops.weld(1.0e-6f));
        if (!mesh.has(AttributeSemantic.NORMAL, 0)) {
            ops.add(Ops.normals(60f));
        }
        if (mesh.has(AttributeSemantic.UV, 0) && !mesh.has(AttributeSemantic.TANGENT, 0)) {
            ops.add(Ops.tangents());
        }
        ops.add(Ops.optimizeVertexCache());
        ops.add(Ops.bounds());
        return ops.toArray(MeshOp[]::new);
    }

    public static MeshOp[] realtimeFastOps(MeshData mesh) {
        List<MeshOp> ops = new ArrayList<>();
        ops.add(Ops.validate());
        ops.add(Ops.removeDegenerates());
        if (!mesh.has(AttributeSemantic.NORMAL, 0)) {
            ops.add(Ops.normals(60f));
        }
        if (mesh.has(AttributeSemantic.UV, 0) && !mesh.has(AttributeSemantic.TANGENT, 0)) {
            ops.add(Ops.tangents());
        }
        ops.add(Ops.bounds());
        return ops.toArray(MeshOp[]::new);
    }
}

