package org.dynamisengine.meshforge.api;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.ops.pipeline.MeshContext;
import org.dynamisengine.meshforge.ops.pipeline.MeshOp;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimeMeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimePackWorkspace;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Preset mesh-processing pipelines.
 */
public final class Pipelines {
    private static final MeshOp VALIDATE = Ops.validate();
    private static final MeshOp REMOVE_DEGENERATES = Ops.removeDegenerates();
    private static final MeshOp BOUNDS = Ops.bounds();

    private Pipelines() {
    }

    /**
     * Precomputed runtime plan for repeated realtime-fast execution.
     * This caches operation selection/config so repeated create calls avoid per-call decision work.
     */
    public static final class RuntimeRealtimePlan {
        private final MeshOp[] ops;
        private final PackSpec packSpec;

        private RuntimeRealtimePlan(MeshOp[] ops, PackSpec packSpec) {
            this.ops = ops;
            this.packSpec = packSpec;
        }
    }

    /**
     * Reusable workspace for runtime planned realtime execution.
     */
    public static final class RuntimeRealtimeWorkspace {
        private final RuntimePackWorkspace packWorkspace = new RuntimePackWorkspace();
    }

    /**
     * Stage-level runtime pipeline profile for realtime-fast execution.
     */
    public static final class RuntimeStageProfile {
        private long totalNs;
        private long validateNs;
        private long removeDegeneratesNs;
        private long normalsNs;
        private long tangentsNs;
        private long boundsNs;
        private boolean trustedFastPathUsed;

        /**
         * Creates an empty profile.
         */
        public RuntimeStageProfile() {
        }

        /**
         * Clears all counters.
         */
        public void reset() {
            totalNs = 0L;
            validateNs = 0L;
            removeDegeneratesNs = 0L;
            normalsNs = 0L;
            tangentsNs = 0L;
            boundsNs = 0L;
            trustedFastPathUsed = false;
        }

        /**
         * Returns totalNs.
         * @return resulting value
         */
        public long totalNs() {
            return totalNs;
        }

        /**
         * Returns validateNs.
         * @return resulting value
         */
        public long validateNs() {
            return validateNs;
        }

        /**
         * Returns removeDegeneratesNs.
         * @return resulting value
         */
        public long removeDegeneratesNs() {
            return removeDegeneratesNs;
        }

        /**
         * Returns normalsNs.
         * @return resulting value
         */
        public long normalsNs() {
            return normalsNs;
        }

        /**
         * Returns tangentsNs.
         * @return resulting value
         */
        public long tangentsNs() {
            return tangentsNs;
        }

        /**
         * Returns boundsNs.
         * @return resulting value
         */
        public long boundsNs() {
            return boundsNs;
        }

        /**
         * Returns whether trusted fast-path was used.
         * @return resulting value
         */
        public boolean trustedFastPathUsed() {
            return trustedFastPathUsed;
        }
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

    /**
     * Trusted-aware fast realtime prep pipeline.
     *
     * @param mesh source mesh
     * @param trustPreprocessed whether trusted path is enabled
     * @param trustedCanonical trusted canonical flag from metadata
     * @param degenerateFree degenerate-free flag from metadata
     * @param hasPrebakedBounds whether prebaked bounds are available
     * @return processed mesh
     */
    public static MeshData realtimeFast(
        MeshData mesh,
        boolean trustPreprocessed,
        boolean trustedCanonical,
        boolean degenerateFree,
        boolean hasPrebakedBounds
    ) {
        if (!shouldUseTrustedFastPath(trustPreprocessed, trustedCanonical, degenerateFree, hasPrebakedBounds)) {
            return realtimeFast(mesh);
        }
        minimalTrustedSanityCheck(mesh, hasPrebakedBounds);
        return runTrustedFast(mesh);
    }

    /**
     * Fast realtime prep pipeline with stage-level profile capture.
     *
     * @param mesh source mesh
     * @param profile destination profile
     * @return processed mesh
     */
    public static MeshData realtimeFastProfiled(MeshData mesh, RuntimeStageProfile profile) {
        return realtimeFastProfiled(mesh, profile, false, false, false, false);
    }

    /**
     * Trusted-aware profiled fast realtime prep pipeline.
     *
     * @param mesh source mesh
     * @param profile destination profile
     * @param trustPreprocessed whether trusted path is enabled
     * @param trustedCanonical trusted canonical flag from metadata
     * @param degenerateFree degenerate-free flag from metadata
     * @param hasPrebakedBounds whether prebaked bounds are available
     * @return processed mesh
     */
    public static MeshData realtimeFastProfiled(
        MeshData mesh,
        RuntimeStageProfile profile,
        boolean trustPreprocessed,
        boolean trustedCanonical,
        boolean degenerateFree,
        boolean hasPrebakedBounds
    ) {
        if (mesh == null) {
            throw new NullPointerException("mesh");
        }
        if (profile == null) {
            throw new NullPointerException("profile");
        }
        profile.reset();
        long totalStart = System.nanoTime();

        if (shouldUseTrustedFastPath(trustPreprocessed, trustedCanonical, degenerateFree, hasPrebakedBounds)) {
            minimalTrustedSanityCheck(mesh, hasPrebakedBounds);
            MeshData trusted = runTrustedFast(mesh);
            profile.trustedFastPathUsed = true;
            profile.totalNs = System.nanoTime() - totalStart;
            return trusted;
        }

        MeshContext context = new MeshContext();
        MeshData current = mesh;

        long opStart = System.nanoTime();
        current = VALIDATE.apply(current, context);
        profile.validateNs = System.nanoTime() - opStart;

        opStart = System.nanoTime();
        current = REMOVE_DEGENERATES.apply(current, context);
        profile.removeDegeneratesNs = System.nanoTime() - opStart;

        if (!current.has(AttributeSemantic.NORMAL, 0)) {
            opStart = System.nanoTime();
            current = Ops.normals(180f).apply(current, context);
            profile.normalsNs = System.nanoTime() - opStart;
        }

        if (current.has(AttributeSemantic.UV, 0) && !current.has(AttributeSemantic.TANGENT, 0)) {
            opStart = System.nanoTime();
            current = Ops.tangents().apply(current, context);
            profile.tangentsNs = System.nanoTime() - opStart;
        }

        opStart = System.nanoTime();
        current = BOUNDS.apply(current, context);
        profile.boundsNs = System.nanoTime() - opStart;

        profile.totalNs = System.nanoTime() - totalStart;
        return current;
    }

    /**
     * Returns realtimeOps.
     * @param mesh parameter value
     * @return resulting value
     */
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

    /**
     * Returns realtimeFastOps.
     * @param mesh parameter value
     * @return resulting value
     */
    public static MeshOp[] realtimeFastOps(MeshData mesh) {
        List<MeshOp> ops = new ArrayList<>();
        ops.add(VALIDATE);
        ops.add(REMOVE_DEGENERATES);
        if (!mesh.has(AttributeSemantic.NORMAL, 0)) {
            // Runtime-focused fast path: use smooth normals without threshold adjacency build.
            ops.add(Ops.normals(180f));
        }
        if (mesh.has(AttributeSemantic.UV, 0) && !mesh.has(AttributeSemantic.TANGENT, 0)) {
            ops.add(Ops.tangents());
        }
        ops.add(BOUNDS);
        return ops.toArray(MeshOp[]::new);
    }

    private static MeshData runTrustedFast(MeshData mesh) {
        MeshContext context = new MeshContext();
        MeshData current = mesh;
        if (!current.has(AttributeSemantic.NORMAL, 0)) {
            current = Ops.normals(180f).apply(current, context);
        }
        if (current.has(AttributeSemantic.UV, 0) && !current.has(AttributeSemantic.TANGENT, 0)) {
            current = Ops.tangents().apply(current, context);
        }
        return current;
    }

    private static boolean shouldUseTrustedFastPath(
        boolean trustPreprocessed,
        boolean trustedCanonical,
        boolean degenerateFree,
        boolean hasPrebakedBounds
    ) {
        return trustPreprocessed && trustedCanonical && degenerateFree && hasPrebakedBounds;
    }

    private static void minimalTrustedSanityCheck(MeshData mesh, boolean hasPrebakedBounds) {
        if (!mesh.has(AttributeSemantic.POSITION, 0)) {
            throw new IllegalStateException("trusted fast path requires POSITION[0]");
        }
        if (mesh.indicesOrNull() == null) {
            throw new IllegalStateException("trusted fast path requires indexed triangles");
        }
        if ((mesh.indicesOrNull().length % 3) != 0) {
            throw new IllegalStateException("trusted fast path requires triangle index count divisible by 3");
        }
        if (!hasPrebakedBounds || mesh.boundsOrNull() == null) {
            throw new IllegalStateException("trusted fast path requires prebaked bounds");
        }
    }

    /**
     * Builds a precomputed realtime-fast runtime plan for one mesh schema/feature shape.
     *
     * @param mesh representative mesh for operation selection
     * @param packSpec pack spec for runtime pack step
     * @return runtime plan
     */
    public static RuntimeRealtimePlan buildRealtimeFastPlan(MeshData mesh, PackSpec packSpec) {
        if (mesh == null) {
            throw new NullPointerException("mesh");
        }
        if (packSpec == null) {
            throw new NullPointerException("packSpec");
        }
        return new RuntimeRealtimePlan(realtimeFastOps(mesh), packSpec);
    }

    /**
     * Executes a precomputed realtime-fast plan and writes packed payload into workspace.
     *
     * @param mesh source mesh
     * @param plan precomputed runtime plan
     * @param workspace reusable destination workspace
     */
    public static void executeRealtimeFastPlanInto(
        MeshData mesh,
        RuntimeRealtimePlan plan,
        RuntimeRealtimeWorkspace workspace
    ) {
        if (mesh == null) {
            throw new NullPointerException("mesh");
        }
        if (plan == null) {
            throw new NullPointerException("plan");
        }
        if (workspace == null) {
            throw new NullPointerException("workspace");
        }
        MeshData processed = runPlan(mesh, plan.ops);
        RuntimeMeshPacker.packInto(processed, plan.packSpec, workspace.packWorkspace);
    }

    private static MeshData runPlan(MeshData mesh, MeshOp[] ops) {
        MeshContext context = new MeshContext();
        MeshData current = mesh;
        for (MeshOp op : ops) {
            current = op.apply(current, context);
        }
        return current;
    }
}
