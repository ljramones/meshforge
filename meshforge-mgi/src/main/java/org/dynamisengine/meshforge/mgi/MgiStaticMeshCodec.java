package org.dynamisengine.meshforge.mgi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal static mesh geometry payload codec for MGI v1.
 */
public final class MgiStaticMeshCodec {
    private static final int MESH_TABLE_ENTRY_INTS = 4;
    private static final int SUBMESH_ENTRY_INTS = 4;
    private static final int BOUNDS_FLOATS = 6;
    private static final int METADATA_INTS = 4;

    private static final int SEM_POSITION = 1;
    private static final int SEM_NORMAL = 2;
    private static final int SEM_UV0 = 3;

    private static final int TYPE_FLOAT32 = 1;

    public byte[] write(MgiStaticMesh mesh) throws IOException {
        if (mesh == null) {
            throw new NullPointerException("mesh");
        }
        validateMesh(mesh);

        byte[] meshTableBytes = encodeMeshTable(mesh);
        byte[] attrSchemaBytes = encodeAttributeSchema(mesh);
        byte[] vertexBytes = encodeVertexStreams(mesh);
        byte[] indexBytes = encodeIndices(mesh.indices());
        byte[] submeshBytes = encodeSubmeshes(mesh.submeshes());
        byte[] boundsBytes = mesh.boundsOrNull() == null ? null : encodeBounds(mesh.boundsOrNull());
        byte[] metadataBytes = mesh.canonicalMetadataOrNull() == null
            ? null
            : encodeMetadata(mesh.canonicalMetadataOrNull());

        long directoryOffset = MgiConstants.HEADER_SIZE_BYTES;
        int chunkCount = 5;
        if (boundsBytes != null) {
            chunkCount++;
        }
        if (metadataBytes != null) {
            chunkCount++;
        }
        long payloadOffset = directoryOffset + ((long) chunkCount * MgiConstants.CHUNK_ENTRY_SIZE_BYTES);

        MgiChunkEntry meshTable = new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), payloadOffset, meshTableBytes.length, 0);
        payloadOffset = meshTable.endExclusive();
        MgiChunkEntry attrSchema = new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), payloadOffset, attrSchemaBytes.length, 0);
        payloadOffset = attrSchema.endExclusive();
        MgiChunkEntry vertices = new MgiChunkEntry(MgiChunkType.VERTEX_STREAMS.id(), payloadOffset, vertexBytes.length, 0);
        payloadOffset = vertices.endExclusive();
        MgiChunkEntry indices = new MgiChunkEntry(MgiChunkType.INDEX_DATA.id(), payloadOffset, indexBytes.length, 0);
        payloadOffset = indices.endExclusive();
        MgiChunkEntry submeshes = new MgiChunkEntry(MgiChunkType.SUBMESH_TABLE.id(), payloadOffset, submeshBytes.length, 0);
        payloadOffset = submeshes.endExclusive();

        List<MgiChunkEntry> entries;
        MgiChunkEntry bounds = null;
        MgiChunkEntry metadata = null;
        if (boundsBytes == null) {
            entries = List.of(meshTable, attrSchema, vertices, indices, submeshes);
        } else {
            bounds = new MgiChunkEntry(MgiChunkType.BOUNDS.id(), payloadOffset, boundsBytes.length, 0);
            entries = List.of(meshTable, attrSchema, vertices, indices, submeshes, bounds);
            payloadOffset = bounds.endExclusive();
        }
        if (metadataBytes != null) {
            metadata = new MgiChunkEntry(MgiChunkType.METADATA.id(), payloadOffset, metadataBytes.length, 0);
            if (bounds == null) {
                entries = List.of(meshTable, attrSchema, vertices, indices, submeshes, metadata);
            } else {
                entries = List.of(meshTable, attrSchema, vertices, indices, submeshes, bounds, metadata);
            }
        }
        MgiHeader header = MgiHeader.v1(entries.size(), directoryOffset, 1);
        long fileSize = metadata != null
            ? metadata.endExclusive()
            : (bounds == null ? submeshes.endExclusive() : bounds.endExclusive());
        MgiValidator.validate(header, entries, fileSize);

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) fileSize);
        MgiWriter writer = new MgiWriter();
        writer.writeHeader(out, header);
        writer.writeChunkDirectory(out, entries);
        out.write(meshTableBytes);
        out.write(attrSchemaBytes);
        out.write(vertexBytes);
        out.write(indexBytes);
        out.write(submeshBytes);
        if (boundsBytes != null) {
            out.write(boundsBytes);
        }
        if (metadataBytes != null) {
            out.write(metadataBytes);
        }
        return out.toByteArray();
    }

    public MgiStaticMesh read(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }

        MgiReader reader = new MgiReader();
        MgiFile file = reader.read(bytes);

        Map<MgiChunkType, MgiChunkEntry> byType = new EnumMap<>(MgiChunkType.class);
        for (MgiChunkEntry entry : file.chunks()) {
            MgiChunkType type = MgiChunkType.fromId(entry.type());
            if (type != null) {
                byType.put(type, entry);
            }
        }

        MgiChunkEntry meshTableEntry = required(byType, MgiChunkType.MESH_TABLE);
        MgiChunkEntry attrSchemaEntry = required(byType, MgiChunkType.ATTRIBUTE_SCHEMA);
        MgiChunkEntry verticesEntry = required(byType, MgiChunkType.VERTEX_STREAMS);
        MgiChunkEntry indicesEntry = required(byType, MgiChunkType.INDEX_DATA);
        MgiChunkEntry submeshEntry = required(byType, MgiChunkType.SUBMESH_TABLE);
        MgiChunkEntry boundsEntry = byType.get(MgiChunkType.BOUNDS);
        MgiChunkEntry metadataEntry = byType.get(MgiChunkType.METADATA);

        int[] meshTable = decodeIntPayload(bytes, meshTableEntry, MESH_TABLE_ENTRY_INTS);
        int vertexCount = meshTable[0];
        int indexCount = meshTable[1];
        int submeshCount = meshTable[2];

        List<AttributeSpec> specs = decodeAttributeSchema(bytes, attrSchemaEntry);
        DecodedStreams streams = decodeVertexStreams(bytes, verticesEntry, specs, vertexCount);

        int[] indices = decodeIntPayload(bytes, indicesEntry, indexCount);
        int[] submeshInts = decodeIntPayload(bytes, submeshEntry, submeshCount * SUBMESH_ENTRY_INTS);

        MgiSubmeshRange[] ranges = new MgiSubmeshRange[submeshCount];
        for (int i = 0; i < submeshCount; i++) {
            int base = i * SUBMESH_ENTRY_INTS;
            ranges[i] = new MgiSubmeshRange(submeshInts[base], submeshInts[base + 1], submeshInts[base + 2]);
        }

        MgiAabb bounds = decodeBounds(bytes, boundsEntry);
        MgiCanonicalMetadata metadata = decodeMetadata(bytes, metadataEntry);

        MgiStaticMesh mesh = new MgiStaticMesh(
            streams.positions,
            streams.normals,
            streams.uv0,
            bounds,
            metadata,
            indices,
            List.of(ranges)
        );
        validateMesh(mesh);
        return mesh;
    }

    private static byte[] encodeMeshTable(MgiStaticMesh mesh) {
        ByteBuffer b = ByteBuffer.allocate(MESH_TABLE_ENTRY_INTS * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(mesh.vertexCount());
        b.putInt(mesh.indices().length);
        b.putInt(mesh.submeshes().size());
        b.putInt(0);
        return b.array();
    }

    private static byte[] encodeAttributeSchema(MgiStaticMesh mesh) {
        ArrayList<AttributeSpec> specs = attributeSpecsFor(mesh);
        ByteBuffer b = ByteBuffer.allocate((1 + (specs.size() * 4)) * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(specs.size());
        for (AttributeSpec spec : specs) {
            b.putInt(spec.semanticId);
            b.putInt(spec.components);
            b.putInt(spec.componentTypeId);
            b.putInt(spec.strideBytes);
        }
        return b.array();
    }

    private static byte[] encodeVertexStreams(MgiStaticMesh mesh) {
        int floats = mesh.positions().length;
        if (mesh.normalsOrNull() != null) {
            floats += mesh.normalsOrNull().length;
        }
        if (mesh.uv0OrNull() != null) {
            floats += mesh.uv0OrNull().length;
        }

        ByteBuffer b = ByteBuffer.allocate(floats * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : mesh.positions()) {
            b.putFloat(v);
        }
        if (mesh.normalsOrNull() != null) {
            for (float v : mesh.normalsOrNull()) {
                b.putFloat(v);
            }
        }
        if (mesh.uv0OrNull() != null) {
            for (float v : mesh.uv0OrNull()) {
                b.putFloat(v);
            }
        }
        return b.array();
    }

    private static byte[] encodeIndices(int[] indices) {
        ByteBuffer b = ByteBuffer.allocate(indices.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int idx : indices) {
            b.putInt(idx);
        }
        return b.array();
    }

    private static byte[] encodeSubmeshes(List<MgiSubmeshRange> submeshes) {
        ByteBuffer b = ByteBuffer.allocate(submeshes.size() * SUBMESH_ENTRY_INTS * Integer.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (MgiSubmeshRange range : submeshes) {
            b.putInt(range.firstIndex());
            b.putInt(range.indexCount());
            b.putInt(range.materialSlot());
            b.putInt(0);
        }
        return b.array();
    }

    private static byte[] encodeBounds(MgiAabb bounds) {
        ByteBuffer b = ByteBuffer.allocate(BOUNDS_FLOATS * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putFloat(bounds.minX());
        b.putFloat(bounds.minY());
        b.putFloat(bounds.minZ());
        b.putFloat(bounds.maxX());
        b.putFloat(bounds.maxY());
        b.putFloat(bounds.maxZ());
        return b.array();
    }

    private static byte[] encodeMetadata(MgiCanonicalMetadata metadata) {
        ByteBuffer b = ByteBuffer.allocate(METADATA_INTS * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(metadata.canonicalVertexCount());
        b.putInt(metadata.canonicalIndexCount());
        b.putInt(metadata.flags());
        b.putInt(0);
        return b.array();
    }

    private static List<AttributeSpec> decodeAttributeSchema(byte[] bytes, MgiChunkEntry entry) {
        if ((entry.lengthBytes() % Integer.BYTES) != 0) {
            throw new MgiValidationException("attribute schema chunk is not int-aligned");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);
        if (b.remaining() < Integer.BYTES) {
            throw new MgiValidationException("attribute schema chunk too small");
        }

        int count = b.getInt();
        if (count <= 0) {
            throw new MgiValidationException("attribute schema must contain at least one attribute");
        }
        if (b.remaining() != count * 4 * Integer.BYTES) {
            throw new MgiValidationException("attribute schema length/count mismatch");
        }

        boolean hasPosition = false;
        ArrayList<AttributeSpec> specs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int semantic = b.getInt();
            int components = b.getInt();
            int componentType = b.getInt();
            int strideBytes = b.getInt();
            AttributeSpec spec = new AttributeSpec(semantic, components, componentType, strideBytes);
            if (semantic == SEM_POSITION) {
                hasPosition = true;
            }
            specs.add(spec);
        }

        if (!hasPosition) {
            throw new MgiValidationException("attribute schema missing POSITION");
        }
        return List.copyOf(specs);
    }

    private static DecodedStreams decodeVertexStreams(
        byte[] bytes,
        MgiChunkEntry entry,
        List<AttributeSpec> specs,
        int vertexCount
    ) {
        if ((entry.lengthBytes() % Float.BYTES) != 0) {
            throw new MgiValidationException("vertex stream chunk is not float-aligned");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);

        float[] positions = null;
        float[] normals = null;
        float[] uv0 = null;

        for (AttributeSpec spec : specs) {
            int floatCount = vertexCount * spec.components;
            int byteCount = floatCount * Float.BYTES;
            if (b.remaining() < byteCount) {
                throw new MgiValidationException("vertex stream underflow for attribute semantic=" + spec.semanticId);
            }

            float[] values = new float[floatCount];
            for (int i = 0; i < values.length; i++) {
                values[i] = b.getFloat();
            }

            if (spec.semanticId == SEM_POSITION) {
                positions = values;
            } else if (spec.semanticId == SEM_NORMAL) {
                normals = values;
            } else if (spec.semanticId == SEM_UV0) {
                uv0 = values;
            }
        }

        if (b.hasRemaining()) {
            throw new MgiValidationException("vertex stream chunk has unexpected trailing payload");
        }
        if (positions == null) {
            throw new MgiValidationException("vertex stream missing POSITION payload");
        }

        return new DecodedStreams(positions, normals, uv0);
    }

    private static int[] decodeIntPayload(byte[] bytes, MgiChunkEntry entry, int expectedCount) {
        if (entry.lengthBytes() != (long) expectedCount * Integer.BYTES) {
            throw new MgiValidationException("unexpected int payload size for chunk type=" + entry.type());
        }
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[expectedCount];
        for (int i = 0; i < out.length; i++) {
            out[i] = b.getInt();
        }
        return out;
    }

    private static MgiAabb decodeBounds(byte[] bytes, MgiChunkEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.lengthBytes() != (long) BOUNDS_FLOATS * Float.BYTES) {
            throw new MgiValidationException("invalid bounds payload size");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);
        return new MgiAabb(
            b.getFloat(),
            b.getFloat(),
            b.getFloat(),
            b.getFloat(),
            b.getFloat(),
            b.getFloat()
        );
    }

    private static MgiCanonicalMetadata decodeMetadata(byte[] bytes, MgiChunkEntry entry) {
        if (entry == null) {
            return null;
        }
        if (entry.lengthBytes() != (long) METADATA_INTS * Integer.BYTES) {
            throw new MgiValidationException("invalid metadata payload size");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);
        return new MgiCanonicalMetadata(
            b.getInt(),
            b.getInt(),
            b.getInt()
        );
    }

    private static MgiChunkEntry required(Map<MgiChunkType, MgiChunkEntry> map, MgiChunkType type) {
        MgiChunkEntry entry = map.get(type);
        if (entry == null) {
            throw new MgiValidationException("missing required chunk: " + type);
        }
        return entry;
    }

    private static ArrayList<AttributeSpec> attributeSpecsFor(MgiStaticMesh mesh) {
        ArrayList<AttributeSpec> specs = new ArrayList<>(3);
        specs.add(new AttributeSpec(SEM_POSITION, 3, TYPE_FLOAT32, 12));
        if (mesh.normalsOrNull() != null) {
            specs.add(new AttributeSpec(SEM_NORMAL, 3, TYPE_FLOAT32, 12));
        }
        if (mesh.uv0OrNull() != null) {
            specs.add(new AttributeSpec(SEM_UV0, 2, TYPE_FLOAT32, 8));
        }
        return specs;
    }

    private static void validateMesh(MgiStaticMesh mesh) {
        int vertexCount = mesh.vertexCount();
        for (int idx : mesh.indices()) {
            if (idx < 0 || idx >= vertexCount) {
                throw new MgiValidationException("index out of vertex range: " + idx + " / vertexCount=" + vertexCount);
            }
        }
        int totalIndexCount = mesh.indices().length;
        for (MgiSubmeshRange submesh : mesh.submeshes()) {
            long end = (long) submesh.firstIndex() + submesh.indexCount();
            if (end > totalIndexCount) {
                throw new MgiValidationException("submesh range exceeds index count");
            }
        }
        if (mesh.canonicalMetadataOrNull() != null) {
            MgiCanonicalMetadata metadata = mesh.canonicalMetadataOrNull();
            if (metadata.canonicalVertexCount() != vertexCount) {
                throw new MgiValidationException("metadata canonicalVertexCount mismatch");
            }
            if (metadata.canonicalIndexCount() != totalIndexCount) {
                throw new MgiValidationException("metadata canonicalIndexCount mismatch");
            }
        }
    }

    private static final class AttributeSpec {
        private final int semanticId;
        private final int components;
        private final int componentTypeId;
        private final int strideBytes;

        private AttributeSpec(int semanticId, int components, int componentTypeId, int strideBytes) {
            this.semanticId = semanticId;
            this.components = components;
            this.componentTypeId = componentTypeId;
            this.strideBytes = strideBytes;

            if (componentTypeId != TYPE_FLOAT32) {
                throw new MgiValidationException("unsupported component type id: " + componentTypeId);
            }
            if (semanticId == SEM_POSITION || semanticId == SEM_NORMAL) {
                if (components != 3 || strideBytes != 12) {
                    throw new MgiValidationException("invalid vec3 float32 layout for semantic=" + semanticId);
                }
            } else if (semanticId == SEM_UV0) {
                if (components != 2 || strideBytes != 8) {
                    throw new MgiValidationException("invalid vec2 float32 layout for semantic=UV0");
                }
            } else {
                throw new MgiValidationException("unsupported semantic id: " + semanticId);
            }
        }
    }

    private record DecodedStreams(float[] positions, float[] normals, float[] uv0) {
    }
}
