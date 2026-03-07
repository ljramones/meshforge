package org.dynamisengine.meshforge.pack.buffer;

import org.dynamisengine.meshforge.pack.layout.VertexLayout;

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
     *
     * @param layout packed vertex layout
     * @param vertexBuffer packed vertex buffer
     * @param indexBuffer packed index buffer
     * @param submeshes submesh index ranges
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
     *
     * @param layout packed vertex layout
     * @param vertexBuffer packed vertex buffer
     * @param indexBuffer packed index buffer
     * @param submeshes submesh index ranges
     * @param meshlets optional meshlet stream
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
     *
     * @param layout packed vertex layout
     * @param vertexBuffer packed vertex buffer
     * @param indexBuffer packed index buffer
     * @param submeshes submesh index ranges
     * @param meshlets optional meshlet stream
     * @param meshletDescriptorBuffer optional descriptor buffer
     * @param meshletDescriptorStrideBytes descriptor stride in bytes
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

    /**
     * Executes layout.
     * @return resulting value
     */
    public VertexLayout layout() {
        return layout;
    }

    /**
     * Executes vertexBuffer.
     * @return resulting value
     */
    public ByteBuffer vertexBuffer() {
        return vertexBuffer;
    }

    /**
     * Executes indexBuffer.
     * @return resulting value
     */
    public IndexBufferView indexBuffer() {
        return indexBuffer;
    }

    /**
     * Returns submeshes.
     * @return resulting value
     */
    public List<SubmeshRange> submeshes() {
        return submeshes;
    }

    /**
     * Executes meshletsOrNull.
     * @return resulting value
     */
    public MeshletBufferView meshletsOrNull() {
        return meshlets;
    }

    /**
     * Checks whether this instance has hasMeshlets.
     * @return resulting value
     */
    public boolean hasMeshlets() {
        return meshlets != null && meshlets.meshletCount() > 0;
    }

    /**
     * Executes meshletDescriptorBufferOrNull.
     * @return resulting value
     */
    public ByteBuffer meshletDescriptorBufferOrNull() {
        return meshletDescriptorBuffer;
    }

    /**
     * Executes meshletDescriptorStrideBytes.
     * @return resulting value
     */
    public int meshletDescriptorStrideBytes() {
        return meshletDescriptorStrideBytes;
    }

    /**
     * Immutable index range + material binding for one submesh.
     *
     * @param firstIndex first index in the packed index buffer
     * @param indexCount number of indices in this range
     * @param materialId optional material identifier
     */
    public record SubmeshRange(int firstIndex, int indexCount, Object materialId) {
    }

    /**
     * Packed index primitive width.
     */
    public enum IndexType {
        /** 16-bit unsigned indices. */
        UINT16,
        /** 32-bit unsigned indices. */
        UINT32
    }

    /**
     * Immutable view of index buffer bytes and metadata.
     *
     * @param type index element width
     * @param buffer underlying index buffer
     * @param indexCount number of indices in the buffer
     */
    public record IndexBufferView(IndexType type, ByteBuffer buffer, int indexCount) {
    }
}
