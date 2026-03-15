package org.dynamisengine.meshforge.gpu;

import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;
import org.dynamisengine.meshforge.pack.packer.RuntimePackWorkspace;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Translator from MeshForge runtime geometry into GPU upload plans.
 */
public final class MeshForgeGpuBridge {
    private MeshForgeGpuBridge() {
    }

    /**
     * Creates a neutral runtime geometry payload from immutable packed mesh output.
     *
     * @param packed packed mesh output
     * @return runtime geometry payload
     */
    public static RuntimeGeometryPayload payloadFromPackedMesh(PackedMesh packed) {
        if (packed == null) {
            throw new NullPointerException("packed");
        }
        PackedMesh.IndexBufferView indexView = packed.indexBuffer();
        ByteBuffer indexBytes = indexView == null ? null : asReadOnly(indexView.buffer());
        PackedMesh.IndexType indexType = indexView == null ? null : indexView.type();
        int indexCount = indexView == null ? 0 : indexView.indexCount();
        int vertexCount = packed.layout().strideBytes() == 0 ? 0 : packed.vertexBuffer().limit() / packed.layout().strideBytes();

        return new RuntimeGeometryPayload(
            packed.layout(),
            vertexCount,
            asReadOnly(packed.vertexBuffer()),
            indexType,
            indexCount,
            indexBytes,
            packed.submeshes()
        );
    }

    /**
     * Creates a neutral runtime geometry payload from runtime workspace buffers.
     *
     * @param layout packed vertex layout used for the workspace
     * @param workspace runtime workspace populated by MeshPacker
     * @return runtime geometry payload
     */
    public static RuntimeGeometryPayload payloadFromRuntimeWorkspace(
        VertexLayout layout,
        RuntimePackWorkspace workspace
    ) {
        if (layout == null) {
            throw new NullPointerException("layout");
        }
        if (workspace == null) {
            throw new NullPointerException("workspace");
        }
        int stride = layout.strideBytes();
        int vertexCount = stride == 0 ? 0 : workspace.vertexBytes() / stride;
        ByteBuffer indexBytes = workspace.indexBufferOrNull() == null ? null : asReadOnly(workspace.indexBufferOrNull());
        PackedMesh.IndexType indexType = workspace.indexTypeOrNull();
        int indexCount = workspace.indexCount();

        List<PackedMesh.SubmeshRange> submeshes = new ArrayList<>(workspace.submeshCount());
        for (int i = 0; i < workspace.submeshCount(); i++) {
            submeshes.add(new PackedMesh.SubmeshRange(
                workspace.submeshFirstIndex(i),
                workspace.submeshIndexCount(i),
                workspace.submeshMaterialId(i)
            ));
        }

        return new RuntimeGeometryPayload(
            layout,
            vertexCount,
            asReadOnly(workspace.vertexBuffer()),
            indexType,
            indexCount,
            indexBytes,
            submeshes
        );
    }

    /**
     * Builds a GPU upload plan from a neutral runtime geometry payload.
     *
     * @param payload runtime geometry payload
     * @return upload plan
     */
    public static GpuGeometryUploadPlan buildUploadPlan(RuntimeGeometryPayload payload) {
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        GpuGeometryUploadPlan.VertexBinding vertex = new GpuGeometryUploadPlan.VertexBinding(
            payload.layout(),
            payload.vertexCount(),
            payload.layout().strideBytes(),
            payload.vertexBytes().remaining()
        );

        GpuGeometryUploadPlan.IndexBinding index = null;
        if (payload.indexType() != null && payload.indexCount() > 0 && payload.indexBytes() != null) {
            index = new GpuGeometryUploadPlan.IndexBinding(
                payload.indexType(),
                payload.indexCount(),
                payload.indexBytes().remaining()
            );
        }

        return new GpuGeometryUploadPlan(vertex, index, payload.submeshes());
    }

    private static ByteBuffer asReadOnly(ByteBuffer buffer) {
        ByteBuffer ro = buffer.asReadOnlyBuffer();
        ro.position(0);
        return ro;
    }
}
