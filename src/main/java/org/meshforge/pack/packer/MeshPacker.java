package org.meshforge.pack.packer;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.pack.buffer.PackedMesh;
import org.meshforge.pack.layout.VertexLayout;
import org.meshforge.pack.spec.PackSpec;
import org.vectrix.gpu.Half;
import org.vectrix.gpu.PackedNorm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class MeshPacker {
    private MeshPacker() {
    }

    public static PackedMesh pack(MeshData mesh, PackSpec spec) {
        if (spec.layoutMode() != PackSpec.LayoutMode.INTERLEAVED) {
            throw new UnsupportedOperationException("v1 supports INTERLEAVED only");
        }

        VertexAttributeView position = require(mesh, AttributeSemantic.POSITION, 0);
        VertexAttributeView normal = optional(mesh, AttributeSemantic.NORMAL, 0);
        VertexAttributeView tangent = optional(mesh, AttributeSemantic.TANGENT, 0);
        VertexAttributeView uv0 = optional(mesh, AttributeSemantic.UV, 0);
        VertexAttributeView color0 = optional(mesh, AttributeSemantic.COLOR, 0);
        VertexAttributeView joints0 = optional(mesh, AttributeSemantic.JOINTS, 0);
        VertexAttributeView weights0 = optional(mesh, AttributeSemantic.WEIGHTS, 0);

        if (spec.failIfMissingNormals() && normal == null) {
            throw new IllegalStateException("Missing NORMAL[0] but PackSpec requires it");
        }
        if (spec.failIfMissingTangents() && tangent == null) {
            throw new IllegalStateException("Missing TANGENT[0] but PackSpec requires it");
        }

        int offset = 0;
        LinkedHashMap<AttributeKey, VertexLayout.Entry> entries = new LinkedHashMap<>();

        offset = add(entries, offset, new AttributeKey(AttributeSemantic.POSITION, 0),
            spec.targetFormat(AttributeSemantic.POSITION, 0));
        if (normal != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.NORMAL, 0),
                spec.targetFormat(AttributeSemantic.NORMAL, 0));
        }
        if (tangent != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.TANGENT, 0),
                spec.targetFormat(AttributeSemantic.TANGENT, 0));
        }
        if (uv0 != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.UV, 0),
                spec.targetFormat(AttributeSemantic.UV, 0));
        }
        if (color0 != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.COLOR, 0),
                spec.targetFormat(AttributeSemantic.COLOR, 0));
        }
        if (joints0 != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.JOINTS, 0),
                spec.targetFormat(AttributeSemantic.JOINTS, 0));
        }
        if (weights0 != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.WEIGHTS, 0),
                spec.targetFormat(AttributeSemantic.WEIGHTS, 0));
        }

        int stride = align(offset, spec.alignmentBytes());
        VertexLayout layout = new VertexLayout(stride, entries);

        int vertexCount = mesh.vertexCount();
        ByteBuffer vertexBuffer = ByteBuffer.allocateDirect(vertexCount * stride).order(ByteOrder.LITTLE_ENDIAN);

        float[] positionData = requireFloat(position, "POSITION");
        float[] normalData = normal == null ? null : normal.rawFloatArrayOrNull();
        float[] tangentData = tangent == null ? null : tangent.rawFloatArrayOrNull();
        float[] uvData = uv0 == null ? null : uv0.rawFloatArrayOrNull();
        float[] colorData = color0 == null ? null : color0.rawFloatArrayOrNull();

        int[] jointsData = joints0 == null ? null : joints0.rawIntArrayOrNull();
        float[] weightsData = weights0 == null ? null : weights0.rawFloatArrayOrNull();

        for (int i = 0; i < vertexCount; i++) {
            int base = i * stride;

            VertexLayout.Entry posEntry = layout.entry(new AttributeKey(AttributeSemantic.POSITION, 0));
            if (posEntry != null) {
                int off = posEntry.offsetBytes();
                int src = i * 3;
                vertexBuffer.putFloat(base + off, positionData[src]);
                vertexBuffer.putFloat(base + off + 4, positionData[src + 1]);
                vertexBuffer.putFloat(base + off + 8, positionData[src + 2]);
            }

            VertexLayout.Entry normalEntry = layout.entry(new AttributeKey(AttributeSemantic.NORMAL, 0));
            if (normalData != null && normalEntry != null) {
                int src = i * 3;
                int packed = PackedNorm.packSnorm8x4(normalData[src], normalData[src + 1], normalData[src + 2], 0.0f);
                vertexBuffer.putInt(base + normalEntry.offsetBytes(), packed);
            }

            VertexLayout.Entry tangentEntry = layout.entry(new AttributeKey(AttributeSemantic.TANGENT, 0));
            if (tangentData != null && tangentEntry != null) {
                int src = i * 4;
                int packed = PackedNorm.packSnorm8x4(
                    tangentData[src], tangentData[src + 1], tangentData[src + 2], tangentData[src + 3]);
                vertexBuffer.putInt(base + tangentEntry.offsetBytes(), packed);
            }

            VertexLayout.Entry uvEntry = layout.entry(new AttributeKey(AttributeSemantic.UV, 0));
            if (uvData != null && uvEntry != null) {
                int src = i * 2;
                vertexBuffer.putShort(base + uvEntry.offsetBytes(), Half.pack(uvData[src]));
                vertexBuffer.putShort(base + uvEntry.offsetBytes() + 2, Half.pack(uvData[src + 1]));
            }

            VertexLayout.Entry colorEntry = layout.entry(new AttributeKey(AttributeSemantic.COLOR, 0));
            if (colorData != null && colorEntry != null) {
                int src = i * 4;
                int packed = PackedNorm.packUnorm8x4(
                    colorData[src], colorData[src + 1], colorData[src + 2], colorData[src + 3]);
                vertexBuffer.putInt(base + colorEntry.offsetBytes(), packed);
            }

            VertexLayout.Entry jointsEntry = layout.entry(new AttributeKey(AttributeSemantic.JOINTS, 0));
            if (jointsData != null && jointsEntry != null) {
                int src = i * 4;
                int packed = (jointsData[src] & 0xFF)
                    | ((jointsData[src + 1] & 0xFF) << 8)
                    | ((jointsData[src + 2] & 0xFF) << 16)
                    | ((jointsData[src + 3] & 0xFF) << 24);
                vertexBuffer.putInt(base + jointsEntry.offsetBytes(), packed);
            }

            VertexLayout.Entry weightsEntry = layout.entry(new AttributeKey(AttributeSemantic.WEIGHTS, 0));
            if (weightsData != null && weightsEntry != null) {
                int src = i * 4;
                int packed = PackedNorm.packUnorm8x4(
                    weightsData[src], weightsData[src + 1], weightsData[src + 2], weightsData[src + 3]);
                vertexBuffer.putInt(base + weightsEntry.offsetBytes(), packed);
            }
        }

        PackedMesh.IndexBufferView indexBuffer = null;
        int[] indices = mesh.indicesOrNull();
        if (indices != null) {
            indexBuffer = packIndices(indices, spec.indexPolicy());
        }

        List<PackedMesh.SubmeshRange> submeshes = new ArrayList<>();
        for (Submesh submesh : mesh.submeshes()) {
            submeshes.add(new PackedMesh.SubmeshRange(submesh.firstIndex(), submesh.indexCount(), submesh.materialId()));
        }

        return new PackedMesh(layout, vertexBuffer, indexBuffer, submeshes);
    }

    private static int add(
        LinkedHashMap<AttributeKey, VertexLayout.Entry> entries,
        int offset,
        AttributeKey key,
        VertexFormat format
    ) {
        if (format == null) {
            return offset;
        }
        entries.put(key, new VertexLayout.Entry(key, format, offset));
        return offset + format.bytesPerVertex();
    }

    private static int align(int value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    private static VertexAttributeView require(MeshData mesh, AttributeSemantic semantic, int setIndex) {
        if (!mesh.has(semantic, setIndex)) {
            throw new IllegalStateException("Missing required attribute: " + semantic + "[" + setIndex + "]");
        }
        return mesh.attribute(semantic, setIndex);
    }

    private static VertexAttributeView optional(MeshData mesh, AttributeSemantic semantic, int setIndex) {
        return mesh.has(semantic, setIndex) ? mesh.attribute(semantic, setIndex) : null;
    }

    private static float[] requireFloat(VertexAttributeView view, String label) {
        float[] values = view.rawFloatArrayOrNull();
        if (values == null) {
            throw new IllegalStateException(label + " must be float-backed in authoring MeshData for v1 packer");
        }
        return values;
    }

    private static PackedMesh.IndexBufferView packIndices(int[] indices, PackSpec.IndexPolicy policy) {
        boolean canUse16 = true;
        for (int value : indices) {
            if ((value & 0xFFFF0000) != 0) {
                canUse16 = false;
                break;
            }
        }
        if (policy == PackSpec.IndexPolicy.FORCE_32) {
            canUse16 = false;
        }

        if (canUse16) {
            ByteBuffer data = ByteBuffer.allocateDirect(indices.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int value : indices) {
                data.putShort((short) value);
            }
            data.flip();
            return new PackedMesh.IndexBufferView(PackedMesh.IndexType.UINT16, data, indices.length);
        }

        ByteBuffer data = ByteBuffer.allocateDirect(indices.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : indices) {
            data.putInt(value);
        }
        data.flip();
        return new PackedMesh.IndexBufferView(PackedMesh.IndexType.UINT32, data, indices.length);
    }
}
