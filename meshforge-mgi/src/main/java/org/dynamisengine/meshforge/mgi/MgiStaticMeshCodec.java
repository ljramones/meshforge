package org.dynamisengine.meshforge.mgi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal static mesh geometry payload codec for MGI v1.
 */
public final class MgiStaticMeshCodec {
    private static final int MESH_TABLE_ENTRY_INTS = 4;
    private static final int ATTR_SCHEMA_INTS = 5;
    private static final int SUBMESH_ENTRY_INTS = 4;

    public byte[] write(MgiStaticMesh mesh) throws IOException {
        if (mesh == null) {
            throw new NullPointerException("mesh");
        }
        validateMesh(mesh);

        byte[] meshTableBytes = encodeMeshTable(mesh);
        byte[] attrSchemaBytes = encodeAttributeSchema();
        byte[] vertexBytes = encodeVertices(mesh.positions());
        byte[] indexBytes = encodeIndices(mesh.indices());
        byte[] submeshBytes = encodeSubmeshes(mesh.submeshes());

        long directoryOffset = MgiConstants.HEADER_SIZE_BYTES;
        int chunkCount = 5;
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

        List<MgiChunkEntry> entries = List.of(meshTable, attrSchema, vertices, indices, submeshes);
        MgiHeader header = MgiHeader.v1(entries.size(), directoryOffset, 1);
        MgiValidator.validate(header, entries, submeshes.endExclusive());

        ByteArrayOutputStream out = new ByteArrayOutputStream((int) submeshes.endExclusive());
        MgiWriter writer = new MgiWriter();
        writer.writeHeader(out, header);
        writer.writeChunkDirectory(out, entries);
        out.write(meshTableBytes);
        out.write(attrSchemaBytes);
        out.write(vertexBytes);
        out.write(indexBytes);
        out.write(submeshBytes);
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

        int[] meshTable = decodeIntPayload(bytes, meshTableEntry, MESH_TABLE_ENTRY_INTS);
        int vertexCount = meshTable[0];
        int indexCount = meshTable[1];
        int submeshCount = meshTable[2];

        int[] attr = decodeIntPayload(bytes, attrSchemaEntry, ATTR_SCHEMA_INTS);
        // v1 minimal schema guard: one position attribute with 3 float32 components and 12-byte stride.
        if (attr[0] != 1 || attr[2] != 3 || attr[4] != 12) {
            throw new MgiValidationException("unsupported attribute schema in minimal static mesh codec");
        }

        float[] positions = decodeFloatPayload(bytes, verticesEntry, vertexCount * 3);
        int[] indices = decodeIntPayload(bytes, indicesEntry, indexCount);
        int[] submeshInts = decodeIntPayload(bytes, submeshEntry, submeshCount * SUBMESH_ENTRY_INTS);

        MgiSubmeshRange[] ranges = new MgiSubmeshRange[submeshCount];
        for (int i = 0; i < submeshCount; i++) {
            int base = i * SUBMESH_ENTRY_INTS;
            ranges[i] = new MgiSubmeshRange(submeshInts[base], submeshInts[base + 1], submeshInts[base + 2]);
        }

        MgiStaticMesh mesh = new MgiStaticMesh(positions, indices, List.of(ranges));
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

    private static byte[] encodeAttributeSchema() {
        ByteBuffer b = ByteBuffer.allocate(ATTR_SCHEMA_INTS * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(1);  // attribute count
        b.putInt(1);  // semantic position
        b.putInt(3);  // component count
        b.putInt(3);  // component type float32
        b.putInt(12); // stride bytes
        return b.array();
    }

    private static byte[] encodeVertices(float[] positions) {
        ByteBuffer b = ByteBuffer.allocate(positions.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : positions) {
            b.putFloat(v);
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

    private static float[] decodeFloatPayload(byte[] bytes, MgiChunkEntry entry, int expectedCount) {
        if (entry.lengthBytes() != (long) expectedCount * Float.BYTES) {
            throw new MgiValidationException("unexpected float payload size for chunk type=" + entry.type());
        }
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[expectedCount];
        for (int i = 0; i < out.length; i++) {
            out[i] = b.getFloat();
        }
        return out;
    }

    private static MgiChunkEntry required(Map<MgiChunkType, MgiChunkEntry> map, MgiChunkType type) {
        MgiChunkEntry entry = map.get(type);
        if (entry == null) {
            throw new MgiValidationException("missing required chunk: " + type);
        }
        return entry;
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
    }
}
