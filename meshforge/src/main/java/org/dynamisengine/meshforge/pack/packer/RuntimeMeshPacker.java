package org.dynamisengine.meshforge.pack.packer;

import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.nio.ByteBuffer;

import static org.dynamisengine.meshforge.pack.packer.MeshPacker.*;

/**
 * Runtime-oriented mesh packing paths that write into caller-owned reusable buffers.
 * These paths avoid per-call allocation and support precomputed plans for repeated packing.
 */
public final class RuntimeMeshPacker {

    private RuntimeMeshPacker() {
    }

    /**
     * Runtime-oriented path that writes packed payload into caller-owned reusable buffers.
     *
     * @param mesh source mesh
     * @param spec pack spec
     * @param workspace caller-owned reusable workspace/destination
     */
    public static void packInto(MeshData mesh, PackSpec spec, RuntimePackWorkspace workspace) {
        packVertexPayloadInto(mesh, spec, workspace);
        packIndexPayloadInto(mesh, spec, workspace);
        captureSubmeshMetadata(mesh, workspace);
    }

    /**
     * Runtime-oriented path with stage-level profile capture.
     *
     * @param mesh source mesh
     * @param spec pack spec
     * @param workspace caller-owned workspace/destination
     * @param profile destination profile
     */
    public static void packIntoProfiled(
        MeshData mesh,
        PackSpec spec,
        RuntimePackWorkspace workspace,
        MeshPacker.RuntimePackProfile profile
    ) {
        if (profile == null) {
            throw new NullPointerException("profile");
        }
        profile.reset();
        long totalStart = System.nanoTime();

        long stageStart = System.nanoTime();
        packVertexPayloadInto(mesh, spec, workspace);
        profile.vertexPayloadNs = System.nanoTime() - stageStart;

        stageStart = System.nanoTime();
        packIndexPayloadInto(mesh, spec, workspace);
        profile.indexPayloadNs = System.nanoTime() - stageStart;

        stageStart = System.nanoTime();
        captureSubmeshMetadata(mesh, workspace);
        profile.submeshMetadataNs = System.nanoTime() - stageStart;

        profile.totalNs = System.nanoTime() - totalStart;
    }

    /**
     * Runtime-oriented vertex payload kernel: writes packed vertex bytes into caller-owned workspace.
     *
     * @param mesh source mesh
     * @param spec pack spec
     * @param workspace caller-owned workspace
     */
    public static void packVertexPayloadInto(MeshData mesh, PackSpec spec, RuntimePackWorkspace workspace) {
        if (workspace == null) {
            throw new NullPointerException("workspace");
        }
        if (spec.layoutMode() != PackSpec.LayoutMode.INTERLEAVED) {
            throw new UnsupportedOperationException("v1 supports INTERLEAVED only");
        }
        if (spec.meshletsEnabled()) {
            throw new UnsupportedOperationException("packInto currently targets non-meshlet runtime path");
        }

        VertexAttributeView position = require(mesh, POSITION_0);
        VertexAttributeView normal = optional(mesh, NORMAL_0);
        VertexAttributeView tangent = optional(mesh, TANGENT_0);
        VertexAttributeView uv0 = optional(mesh, UV_0);
        VertexAttributeView color0 = optional(mesh, COLOR_0);
        VertexAttributeView joints0 = optional(mesh, JOINTS_0);
        VertexAttributeView weights0 = optional(mesh, WEIGHTS_0);

        if (spec.failIfMissingNormals() && normal == null) {
            throw new IllegalStateException("Missing NORMAL[0] but PackSpec requires it");
        }
        if (spec.failIfMissingTangents() && tangent == null) {
            throw new IllegalStateException("Missing TANGENT[0] but PackSpec requires it");
        }

        int layoutMask = 0;
        if (normal != null) {
            layoutMask |= MASK_NORMAL;
        }
        if (tangent != null) {
            layoutMask |= MASK_TANGENT;
        }
        if (uv0 != null) {
            layoutMask |= MASK_UV;
        }
        if (color0 != null) {
            layoutMask |= MASK_COLOR;
        }
        if (joints0 != null) {
            layoutMask |= MASK_JOINTS;
        }
        if (weights0 != null) {
            layoutMask |= MASK_WEIGHTS;
        }

        VertexFormat positionFormat = spec.targetFormat(POSITION_0);
        if (positionFormat == null) {
            throw new IllegalStateException("PackSpec must include target format for POSITION[0]");
        }
        VertexLayout layout = workspace.resolveLayout(
            spec,
            layoutMask,
            positionFormat,
            spec.targetFormat(NORMAL_0),
            spec.targetFormat(TANGENT_0),
            spec.targetFormat(UV_0),
            spec.targetFormat(COLOR_0),
            spec.targetFormat(JOINTS_0),
            spec.targetFormat(WEIGHTS_0)
        );

        int vertexCount = mesh.vertexCount();
        int stride = layout.strideBytes();
        final ByteBuffer vertexBuffer;
        try {
            vertexBuffer = workspace.ensureVertexBufferCapacity(Math.multiplyExact(vertexCount, stride));
        } catch (ArithmeticException ex) {
            throw new IllegalStateException(
                "Vertex buffer size overflow: vertexCount=" + vertexCount + ", stride=" + stride, ex);
        }

        float[] positionData = requireFloat(position, "POSITION");
        float[] normalData = normal == null ? null : normal.rawFloatArrayOrNull();
        float[] tangentData = tangent == null ? null : tangent.rawFloatArrayOrNull();
        float[] uvData = uv0 == null ? null : uv0.rawFloatArrayOrNull();
        float[] colorData = color0 == null ? null : color0.rawFloatArrayOrNull();
        int[] jointsData = joints0 == null ? null : joints0.rawIntArrayOrNull();
        float[] weightsData = weights0 == null ? null : weights0.rawFloatArrayOrNull();

        VertexLayout.Entry posEntry = layout.entry(POSITION_0);
        VertexLayout.Entry normalEntry = layout.entry(NORMAL_0);
        VertexLayout.Entry tangentEntry = layout.entry(TANGENT_0);
        VertexLayout.Entry uvEntry = layout.entry(UV_0);
        VertexLayout.Entry colorEntry = layout.entry(COLOR_0);
        VertexLayout.Entry jointsEntry = layout.entry(JOINTS_0);
        VertexLayout.Entry weightsEntry = layout.entry(WEIGHTS_0);

        int posOff = posEntry == null ? -1 : posEntry.offsetBytes();
        int normalOff = normalEntry == null ? -1 : normalEntry.offsetBytes();
        int tangentOff = tangentEntry == null ? -1 : tangentEntry.offsetBytes();
        int uvOff = uvEntry == null ? -1 : uvEntry.offsetBytes();
        int colorOff = colorEntry == null ? -1 : colorEntry.offsetBytes();
        int jointsOff = jointsEntry == null ? -1 : jointsEntry.offsetBytes();
        int weightsOff = weightsEntry == null ? -1 : weightsEntry.offsetBytes();

        boolean hasNormal = normalData != null && normalOff >= 0;
        boolean hasTangent = tangentData != null && tangentOff >= 0;
        boolean hasUv = uvData != null && uvOff >= 0;
        boolean hasColor = colorData != null && colorOff >= 0;
        boolean hasJoints = joints0 != null && jointsOff >= 0;
        boolean hasWeights = weightsData != null && weightsOff >= 0;
        VertexFormat normalFormat = normalEntry == null ? null : normalEntry.format();

        VertexWriteOps.writeFusedPass(
            vertexBuffer,
            vertexCount,
            stride,
            positionData,
            posOff,
            normalData,
            hasNormal,
            normalOff,
            normalFormat,
            tangentData,
            hasTangent,
            tangentOff,
            uvData,
            hasUv,
            uvOff,
            colorData,
            hasColor,
            colorOff,
            jointsData,
            joints0,
            hasJoints,
            jointsOff,
            weightsData,
            hasWeights,
            weightsOff
        );
    }

    /**
     * Builds a precomputed runtime packing plan for repeated packing of the same mesh/spec shape.
     *
     * @param mesh source mesh
     * @param spec pack spec
     * @return precomputed runtime pack plan
     */
    public static RuntimePackPlan buildRuntimePlan(MeshData mesh, PackSpec spec) {
        if (mesh == null) {
            throw new NullPointerException("mesh");
        }
        if (spec == null) {
            throw new NullPointerException("spec");
        }
        if (spec.layoutMode() != PackSpec.LayoutMode.INTERLEAVED) {
            throw new UnsupportedOperationException("v1 supports INTERLEAVED only");
        }
        if (spec.meshletsEnabled()) {
            throw new UnsupportedOperationException("packInto currently targets non-meshlet runtime path");
        }

        VertexAttributeView position = require(mesh, POSITION_0);
        VertexAttributeView normal = optional(mesh, NORMAL_0);
        VertexAttributeView tangent = optional(mesh, TANGENT_0);
        VertexAttributeView uv0 = optional(mesh, UV_0);
        VertexAttributeView color0 = optional(mesh, COLOR_0);
        VertexAttributeView joints0 = optional(mesh, JOINTS_0);
        VertexAttributeView weights0 = optional(mesh, WEIGHTS_0);

        if (spec.failIfMissingNormals() && normal == null) {
            throw new IllegalStateException("Missing NORMAL[0] but PackSpec requires it");
        }
        if (spec.failIfMissingTangents() && tangent == null) {
            throw new IllegalStateException("Missing TANGENT[0] but PackSpec requires it");
        }

        int layoutMask = 0;
        if (normal != null) {
            layoutMask |= MASK_NORMAL;
        }
        if (tangent != null) {
            layoutMask |= MASK_TANGENT;
        }
        if (uv0 != null) {
            layoutMask |= MASK_UV;
        }
        if (color0 != null) {
            layoutMask |= MASK_COLOR;
        }
        if (joints0 != null) {
            layoutMask |= MASK_JOINTS;
        }
        if (weights0 != null) {
            layoutMask |= MASK_WEIGHTS;
        }

        VertexFormat positionFormat = spec.targetFormat(POSITION_0);
        if (positionFormat == null) {
            throw new IllegalStateException("PackSpec must include target format for POSITION[0]");
        }
        VertexLayout layout = buildLayout(
            spec,
            layoutMask,
            positionFormat,
            spec.targetFormat(NORMAL_0),
            spec.targetFormat(TANGENT_0),
            spec.targetFormat(UV_0),
            spec.targetFormat(COLOR_0),
            spec.targetFormat(JOINTS_0),
            spec.targetFormat(WEIGHTS_0)
        );

        int vertexCount = mesh.vertexCount();
        int stride = layout.strideBytes();

        float[] positionData = requireFloat(position, "POSITION");
        float[] normalData = normal == null ? null : normal.rawFloatArrayOrNull();
        float[] tangentData = tangent == null ? null : tangent.rawFloatArrayOrNull();
        float[] uvData = uv0 == null ? null : uv0.rawFloatArrayOrNull();
        float[] colorData = color0 == null ? null : color0.rawFloatArrayOrNull();
        int[] jointsData = joints0 == null ? null : joints0.rawIntArrayOrNull();
        float[] weightsData = weights0 == null ? null : weights0.rawFloatArrayOrNull();

        VertexLayout.Entry posEntry = layout.entry(POSITION_0);
        VertexLayout.Entry normalEntry = layout.entry(NORMAL_0);
        VertexLayout.Entry tangentEntry = layout.entry(TANGENT_0);
        VertexLayout.Entry uvEntry = layout.entry(UV_0);
        VertexLayout.Entry colorEntry = layout.entry(COLOR_0);
        VertexLayout.Entry jointsEntry = layout.entry(JOINTS_0);
        VertexLayout.Entry weightsEntry = layout.entry(WEIGHTS_0);

        int posOff = posEntry == null ? -1 : posEntry.offsetBytes();
        int normalOff = normalEntry == null ? -1 : normalEntry.offsetBytes();
        int tangentOff = tangentEntry == null ? -1 : tangentEntry.offsetBytes();
        int uvOff = uvEntry == null ? -1 : uvEntry.offsetBytes();
        int colorOff = colorEntry == null ? -1 : colorEntry.offsetBytes();
        int jointsOff = jointsEntry == null ? -1 : jointsEntry.offsetBytes();
        int weightsOff = weightsEntry == null ? -1 : weightsEntry.offsetBytes();

        boolean hasNormal = normalData != null && normalOff >= 0;
        boolean hasTangent = tangentData != null && tangentOff >= 0;
        boolean hasUv = uvData != null && uvOff >= 0;
        boolean hasColor = colorData != null && colorOff >= 0;
        boolean hasJoints = joints0 != null && jointsOff >= 0;
        boolean hasWeights = weightsData != null && weightsOff >= 0;
        VertexFormat normalFormat = normalEntry == null ? null : normalEntry.format();

        return new RuntimePackPlan(
            layout,
            vertexCount,
            stride,
            positionData,
            posOff,
            normalData,
            hasNormal,
            normalOff,
            normalFormat,
            tangentData,
            hasTangent,
            tangentOff,
            uvData,
            hasUv,
            uvOff,
            colorData,
            hasColor,
            colorOff,
            jointsData,
            joints0,
            hasJoints,
            jointsOff,
            weightsData,
            hasWeights,
            weightsOff,
            mesh.indicesOrNull(),
            spec.indexPolicy(),
            mesh.submeshes()
        );
    }

    /**
     * Executes runtime packing from a precomputed plan.
     *
     * @param plan precomputed runtime plan
     * @param workspace caller-owned workspace
     */
    public static void packPlannedInto(RuntimePackPlan plan, RuntimePackWorkspace workspace) {
        packVertexPayloadInto(plan, workspace);
        packIndexPayloadInto(plan, workspace);
        captureSubmeshMetadata(plan, workspace);
    }

    /**
     * Runtime packing from a precomputed plan with stage-level profile capture.
     *
     * @param plan precomputed runtime plan
     * @param workspace caller-owned workspace
     * @param profile destination profile
     */
    public static void packPlannedIntoProfiled(
        RuntimePackPlan plan,
        RuntimePackWorkspace workspace,
        MeshPacker.RuntimePackProfile profile
    ) {
        if (profile == null) {
            throw new NullPointerException("profile");
        }
        profile.reset();
        long totalStart = System.nanoTime();

        long stageStart = System.nanoTime();
        packVertexPayloadInto(plan, workspace);
        profile.vertexPayloadNs = System.nanoTime() - stageStart;

        stageStart = System.nanoTime();
        packIndexPayloadInto(plan, workspace);
        profile.indexPayloadNs = System.nanoTime() - stageStart;

        stageStart = System.nanoTime();
        captureSubmeshMetadata(plan, workspace);
        profile.submeshMetadataNs = System.nanoTime() - stageStart;

        profile.totalNs = System.nanoTime() - totalStart;
    }

    /**
     * Runtime-oriented vertex payload kernel using a precomputed plan.
     *
     * @param plan precomputed runtime plan
     * @param workspace caller-owned workspace
     */
    public static void packVertexPayloadInto(RuntimePackPlan plan, RuntimePackWorkspace workspace) {
        if (plan == null) {
            throw new NullPointerException("plan");
        }
        if (workspace == null) {
            throw new NullPointerException("workspace");
        }

        final ByteBuffer vertexBuffer;
        try {
            vertexBuffer = workspace.ensureVertexBufferCapacity(Math.multiplyExact(plan.vertexCount, plan.stride));
        } catch (ArithmeticException ex) {
            throw new IllegalStateException(
                "Vertex buffer size overflow: vertexCount=" + plan.vertexCount + ", stride=" + plan.stride, ex);
        }

        VertexWriteOps.writeFusedPass(
            vertexBuffer,
            plan.vertexCount,
            plan.stride,
            plan.positionData,
            plan.posOff,
            plan.normalData,
            plan.hasNormal,
            plan.normalOff,
            plan.normalFormat,
            plan.tangentData,
            plan.hasTangent,
            plan.tangentOff,
            plan.uvData,
            plan.hasUv,
            plan.uvOff,
            plan.colorData,
            plan.hasColor,
            plan.colorOff,
            plan.jointsData,
            plan.jointsView,
            plan.hasJoints,
            plan.jointsOff,
            plan.weightsData,
            plan.hasWeights,
            plan.weightsOff
        );
    }

    /**
     * Runtime-oriented index payload kernel using a precomputed plan.
     *
     * @param plan precomputed runtime plan
     * @param workspace caller-owned workspace
     */
    public static void packIndexPayloadInto(RuntimePackPlan plan, RuntimePackWorkspace workspace) {
        if (plan == null) {
            throw new NullPointerException("plan");
        }
        if (workspace == null) {
            throw new NullPointerException("workspace");
        }
        if (plan.indices == null || plan.indices.length == 0) {
            workspace.clearIndexPayload();
            return;
        }
        IndexPacker.packIndicesInto(plan.indices, plan.indexPolicy, workspace);
    }

    /**
     * Runtime-oriented index payload kernel: writes packed index bytes into caller-owned workspace.
     *
     * @param mesh source mesh
     * @param spec pack spec
     * @param workspace caller-owned workspace
     */
    public static void packIndexPayloadInto(MeshData mesh, PackSpec spec, RuntimePackWorkspace workspace) {
        if (workspace == null) {
            throw new NullPointerException("workspace");
        }
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length == 0) {
            workspace.clearIndexPayload();
            return;
        }
        IndexPacker.packIndicesInto(indices, spec.indexPolicy(), workspace);
    }

    /**
     * Captures submesh metadata from a precomputed runtime plan.
     *
     * @param plan precomputed runtime plan
     * @param workspace caller-owned workspace
     */
    public static void captureSubmeshMetadata(RuntimePackPlan plan, RuntimePackWorkspace workspace) {
        if (plan == null) {
            throw new NullPointerException("plan");
        }
        if (workspace == null) {
            throw new NullPointerException("workspace");
        }
        workspace.setSubmeshes(plan.submeshes);
    }

    /**
     * Captures submesh metadata in caller-owned reusable workspace arrays.
     *
     * @param mesh source mesh
     * @param workspace caller-owned workspace
     */
    public static void captureSubmeshMetadata(MeshData mesh, RuntimePackWorkspace workspace) {
        if (workspace == null) {
            throw new NullPointerException("workspace");
        }
        workspace.setSubmeshes(mesh.submeshes());
    }
}
