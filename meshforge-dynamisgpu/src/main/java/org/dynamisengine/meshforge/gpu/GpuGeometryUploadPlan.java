package org.dynamisengine.meshforge.gpu;

import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;

import java.util.List;

/**
 * GPU-facing upload plan derived from MeshForge runtime geometry payload.
 *
 * @param vertexBinding vertex buffer binding plan
 * @param indexBinding optional index buffer binding plan
 * @param submeshes draw-range metadata
 */
public record GpuGeometryUploadPlan(
    VertexBinding vertexBinding,
    IndexBinding indexBinding,
    List<PackedMesh.SubmeshRange> submeshes
) {
    public GpuGeometryUploadPlan {
        if (vertexBinding == null) {
            throw new NullPointerException("vertexBinding");
        }
        if (submeshes == null) {
            throw new NullPointerException("submeshes");
        }
        submeshes = List.copyOf(submeshes);
    }

    /**
     * Vertex buffer upload + binding metadata.
     *
     * @param layout packed vertex layout
     * @param vertexCount vertex count
     * @param strideBytes vertex stride
     * @param byteSize upload byte size
     */
    public record VertexBinding(VertexLayout layout, int vertexCount, int strideBytes, int byteSize) {
    }

    /**
     * Index buffer upload + binding metadata.
     *
     * @param type index type
     * @param indexCount index count
     * @param byteSize upload byte size
     */
    public record IndexBinding(PackedMesh.IndexType type, int indexCount, int byteSize) {
    }
}
