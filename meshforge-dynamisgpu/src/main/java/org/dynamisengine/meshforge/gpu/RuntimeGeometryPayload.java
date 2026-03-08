package org.dynamisengine.meshforge.gpu;

import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Neutral runtime geometry payload produced by MeshForge for GPU upload planning.
 *
 * @param layout packed vertex layout
 * @param vertexCount vertex count
 * @param vertexBytes packed vertex bytes
 * @param indexType index type or {@code null} when non-indexed
 * @param indexCount index count
 * @param indexBytes packed index bytes or {@code null} when non-indexed
 * @param submeshes submesh index ranges
 */
public record RuntimeGeometryPayload(
    VertexLayout layout,
    int vertexCount,
    ByteBuffer vertexBytes,
    PackedMesh.IndexType indexType,
    int indexCount,
    ByteBuffer indexBytes,
    List<PackedMesh.SubmeshRange> submeshes
) {
    public RuntimeGeometryPayload {
        if (layout == null) {
            throw new NullPointerException("layout");
        }
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount < 0");
        }
        if (vertexBytes == null) {
            throw new NullPointerException("vertexBytes");
        }
        if (indexCount < 0) {
            throw new IllegalArgumentException("indexCount < 0");
        }
        if (submeshes == null) {
            throw new NullPointerException("submeshes");
        }
        submeshes = List.copyOf(submeshes);
    }
}
