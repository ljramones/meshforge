package org.dynamisengine.meshforge.pack.packer;

import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Reusable destination/scratch for allocation-disciplined runtime pack paths.
 */
public final class RuntimePackWorkspace {
    private ByteBuffer vertexBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN);
    private ByteBuffer indexBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN);
    private PackedMesh.IndexType indexType;
    private int indexCount;
    private VertexLayout cachedLayout;
    private PackSpec cachedSpec;
    private int cachedLayoutMask = Integer.MIN_VALUE;
    private int[] submeshFirst = new int[0];
    private int[] submeshCount = new int[0];
    private Object[] submeshMaterial = new Object[0];
    private int submeshSize;

    public ByteBuffer vertexBuffer() {
        return vertexBuffer;
    }

    public ByteBuffer indexBufferOrNull() {
        return indexCount == 0 ? null : indexBuffer;
    }

    public PackedMesh.IndexType indexTypeOrNull() {
        return indexType;
    }

    public int indexCount() {
        return indexCount;
    }

    public int vertexBytes() {
        return vertexBuffer.limit();
    }

    public int indexBytes() {
        return indexCount == 0 ? 0 : indexBuffer.limit();
    }

    public int submeshCount() {
        return submeshSize;
    }

    public int submeshFirstIndex(int index) {
        if (index < 0 || index >= submeshSize) {
            throw new IndexOutOfBoundsException("submesh index out of range: " + index);
        }
        return submeshFirst[index];
    }

    public int submeshIndexCount(int index) {
        if (index < 0 || index >= submeshSize) {
            throw new IndexOutOfBoundsException("submesh index out of range: " + index);
        }
        return submeshCount[index];
    }

    public Object submeshMaterialId(int index) {
        if (index < 0 || index >= submeshSize) {
            throw new IndexOutOfBoundsException("submesh index out of range: " + index);
        }
        return submeshMaterial[index];
    }

    VertexLayout resolveLayout(
        PackSpec spec,
        int layoutMask,
        VertexFormat positionFormat,
        VertexFormat normalFormat,
        VertexFormat tangentFormat,
        VertexFormat uvFormat,
        VertexFormat colorFormat,
        VertexFormat jointsFormat,
        VertexFormat weightsFormat
    ) {
        if (cachedLayout != null && cachedSpec == spec && cachedLayoutMask == layoutMask) {
            return cachedLayout;
        }

        int offset = 0;
        LinkedHashMap<AttributeKey, VertexLayout.Entry> entries = new LinkedHashMap<>();
        offset = MeshPacker.add(entries, offset, MeshPacker.POSITION_0, positionFormat);
        if ((layoutMask & MeshPacker.MASK_NORMAL) != 0) {
            offset = MeshPacker.add(entries, offset, MeshPacker.NORMAL_0, normalFormat);
        }
        if ((layoutMask & MeshPacker.MASK_TANGENT) != 0) {
            offset = MeshPacker.add(entries, offset, MeshPacker.TANGENT_0, tangentFormat);
        }
        if ((layoutMask & MeshPacker.MASK_UV) != 0) {
            offset = MeshPacker.add(entries, offset, MeshPacker.UV_0, uvFormat);
        }
        if ((layoutMask & MeshPacker.MASK_COLOR) != 0) {
            offset = MeshPacker.add(entries, offset, MeshPacker.COLOR_0, colorFormat);
        }
        if ((layoutMask & MeshPacker.MASK_JOINTS) != 0) {
            offset = MeshPacker.add(entries, offset, MeshPacker.JOINTS_0, jointsFormat);
        }
        if ((layoutMask & MeshPacker.MASK_WEIGHTS) != 0) {
            offset = MeshPacker.add(entries, offset, MeshPacker.WEIGHTS_0, weightsFormat);
        }
        int stride = MeshPacker.align(offset, spec.alignmentBytes());
        cachedLayout = new VertexLayout(stride, entries);
        cachedSpec = spec;
        cachedLayoutMask = layoutMask;
        return cachedLayout;
    }

    ByteBuffer ensureVertexBufferCapacity(int requiredBytes) {
        if (vertexBuffer.capacity() < requiredBytes) {
            vertexBuffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.LITTLE_ENDIAN);
        }
        vertexBuffer.clear();
        vertexBuffer.limit(requiredBytes);
        return vertexBuffer;
    }

    ByteBuffer ensureIndexBufferCapacity(int requiredBytes) {
        if (indexBuffer.capacity() < requiredBytes) {
            indexBuffer = ByteBuffer.allocateDirect(requiredBytes).order(ByteOrder.LITTLE_ENDIAN);
        }
        indexBuffer.clear();
        indexBuffer.limit(requiredBytes);
        return indexBuffer;
    }

    void setIndexPayload(PackedMesh.IndexType type, int count, int bytes) {
        this.indexType = type;
        this.indexCount = count;
        indexBuffer.limit(bytes);
        indexBuffer.position(0);
    }

    void clearIndexPayload() {
        this.indexType = null;
        this.indexCount = 0;
        indexBuffer.clear();
        indexBuffer.limit(0);
    }

    private void ensureSubmeshCapacity(int required) {
        if (submeshFirst.length < required) {
            submeshFirst = new int[required];
            submeshCount = new int[required];
            submeshMaterial = new Object[required];
        }
    }

    void setSubmeshes(List<Submesh> source) {
        int size = source.size();
        ensureSubmeshCapacity(size);
        for (int i = 0; i < size; i++) {
            Submesh submesh = source.get(i);
            submeshFirst[i] = submesh.firstIndex();
            submeshCount[i] = submesh.indexCount();
            submeshMaterial[i] = submesh.materialId();
        }
        submeshSize = size;
    }
}
