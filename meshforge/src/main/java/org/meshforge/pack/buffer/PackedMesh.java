package org.meshforge.pack.buffer;

import org.meshforge.pack.layout.VertexLayout;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Immutable runtime-oriented mesh buffer payload produced by the packer.
 * <p>
 * The object contains packed vertex/index data, submesh index ranges, and
 * optional meshlet streams/descriptor metadata.
 */
public final class PackedMesh {
    private final VertexLayout layout;
    private final ByteBuffer vertexBuffer;
    private final IndexBufferView indexBuffer;
    private final List<SubmeshRange> submeshes;
    private final MeshletBufferView meshlets;
    private final ByteBuffer meshletDescriptorBuffer;
    private final int meshletDescriptorStrideBytes;

    /**
     * Creates a packed mesh without meshlet streams.
     */
    public PackedMesh(
        VertexLayout layout,
        ByteBuffer vertexBuffer,
        IndexBufferView indexBuffer,
        List<SubmeshRange> submeshes
    ) {
        this(layout, vertexBuffer, indexBuffer, submeshes, null);
    }

    /**
     * Creates a packed mesh with optional meshlet stream.
     */
    public PackedMesh(
        VertexLayout layout,
        ByteBuffer vertexBuffer,
        IndexBufferView indexBuffer,
        List<SubmeshRange> submeshes,
        MeshletBufferView meshlets
    ) {
        this(layout, vertexBuffer, indexBuffer, submeshes, meshlets, null, 0);
    }

    /**
     * Creates a packed mesh with full optional meshlet descriptor payload.
     */
    public PackedMesh(
        VertexLayout layout,
        ByteBuffer vertexBuffer,
        IndexBufferView indexBuffer,
        List<SubmeshRange> submeshes,
        MeshletBufferView meshlets,
        ByteBuffer meshletDescriptorBuffer,
        int meshletDescriptorStrideBytes
    ) {
        this.layout = layout;
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.submeshes = List.copyOf(submeshes);
        this.meshlets = meshlets;
        this.meshletDescriptorBuffer = meshletDescriptorBuffer;
        this.meshletDescriptorStrideBytes = meshletDescriptorStrideBytes;
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

    public MeshletBufferView meshletsOrNull() {
        return meshlets;
    }

    public boolean hasMeshlets() {
        return meshlets != null && meshlets.meshletCount() > 0;
    }

    public ByteBuffer meshletDescriptorBufferOrNull() {
        return meshletDescriptorBuffer;
    }

    public int meshletDescriptorStrideBytes() {
        return meshletDescriptorStrideBytes;
    }

    /**
     * Immutable index range + material binding for one submesh.
     */
    public record SubmeshRange(int firstIndex, int indexCount, Object materialId) {
    }

    /**
     * Packed index primitive width.
     */
    public enum IndexType { UINT16, UINT32 }

    /**
     * Immutable view of index buffer bytes and metadata.
     */
    public record IndexBufferView(IndexType type, ByteBuffer buffer, int indexCount) {
    }
}
