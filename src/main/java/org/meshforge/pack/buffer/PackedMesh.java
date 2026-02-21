package org.meshforge.pack.buffer;

import org.meshforge.pack.layout.VertexLayout;

import java.nio.ByteBuffer;
import java.util.List;

public final class PackedMesh {
    private final VertexLayout layout;
    private final ByteBuffer vertexBuffer;
    private final IndexBufferView indexBuffer;
    private final List<SubmeshRange> submeshes;

    public PackedMesh(
        VertexLayout layout,
        ByteBuffer vertexBuffer,
        IndexBufferView indexBuffer,
        List<SubmeshRange> submeshes
    ) {
        this.layout = layout;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.submeshes = List.copyOf(submeshes);
    }

    public VertexLayout layout() {
        return layout;
    }

    public ByteBuffer vertexBuffer() {
        return vertexBuffer;
    }

    public IndexBufferView indexBuffer() {
        return indexBuffer;
    }

    public List<SubmeshRange> submeshes() {
        return submeshes;
    }

    public record SubmeshRange(int firstIndex, int indexCount, Object materialId) {
    }

    public enum IndexType { UINT16, UINT32 }

    public record IndexBufferView(IndexType type, ByteBuffer buffer, int indexCount) {
    }
}
