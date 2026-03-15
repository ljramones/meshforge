package org.dynamisengine.meshforge.mgi;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Package-private reader for MGI v1 static mesh geometry payloads.
 */
final class MgiStaticMeshReader {
    private static final int MESH_TABLE_ENTRY_INTS = MgiStaticMeshWriter.MESH_TABLE_ENTRY_INTS;
    private static final int SUBMESH_ENTRY_INTS = MgiStaticMeshWriter.SUBMESH_ENTRY_INTS;
    private static final int BOUNDS_FLOATS = MgiStaticMeshWriter.BOUNDS_FLOATS;
    private static final int METADATA_INTS = MgiStaticMeshWriter.METADATA_INTS;
    private static final int MESHLET_DESCRIPTOR_INTS = MgiStaticMeshWriter.MESHLET_DESCRIPTOR_INTS;
    private static final int MESHLET_BOUNDS_FLOATS = MgiStaticMeshWriter.MESHLET_BOUNDS_FLOATS;
    private static final int MESHLET_LOD_LEVEL_BYTES = MgiStaticMeshWriter.MESHLET_LOD_LEVEL_BYTES;
    private static final int MESHLET_STREAM_UNIT_INTS = MgiStaticMeshWriter.MESHLET_STREAM_UNIT_INTS;
    private static final int RT_REGION_INTS = MgiStaticMeshWriter.RT_REGION_INTS;
    private static final int TESSELLATION_REGION_BYTES = MgiStaticMeshWriter.TESSELLATION_REGION_BYTES;

    private static final int SEM_POSITION = MgiStaticMeshWriter.SEM_POSITION;
    private static final int SEM_NORMAL = MgiStaticMeshWriter.SEM_NORMAL;
    private static final int SEM_UV0 = MgiStaticMeshWriter.SEM_UV0;

    private static final int TYPE_FLOAT32 = MgiStaticMeshWriter.TYPE_FLOAT32;

    MgiStaticMesh read(byte[] bytes) throws IOException {
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
        MgiChunkEntry meshletDescriptorEntry = byType.get(MgiChunkType.MESHLET_DESCRIPTORS);
        MgiChunkEntry meshletRemapEntry = byType.get(MgiChunkType.MESHLET_VERTEX_REMAP);
        MgiChunkEntry meshletTriangleEntry = byType.get(MgiChunkType.MESHLET_TRIANGLES);
        MgiChunkEntry meshletBoundsEntry = byType.get(MgiChunkType.MESHLET_BOUNDS);
        MgiChunkEntry meshletLodEntry = byType.get(MgiChunkType.MESHLET_LOD_LEVELS);
        MgiChunkEntry meshletStreamEntry = byType.get(MgiChunkType.MESHLET_STREAM_UNITS);
        MgiChunkEntry rtRegionsEntry = byType.get(MgiChunkType.RAY_TRACING_REGIONS);
        MgiChunkEntry tessellationRegionsEntry = byType.get(MgiChunkType.TESSELLATION_REGIONS);

        int[] meshTable = decodeIntPayload(bytes, meshTableEntry, MESH_TABLE_ENTRY_INTS);
        int vertexCount = meshTable[0];
        int indexCount = meshTable[1];
        int submeshCount = meshTable[2];

        List<MgiStaticMeshWriter.AttributeSpec> specs = decodeAttributeSchema(bytes, attrSchemaEntry);
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
        MgiMeshletData meshletData = decodeMeshlets(
            bytes,
            meshletDescriptorEntry,
            meshletRemapEntry,
            meshletTriangleEntry,
            meshletBoundsEntry,
            submeshCount
        );
        MgiMeshletLodData meshletLodData = decodeMeshletLodLevels(bytes, meshletLodEntry);
        MgiMeshletStreamingData meshletStreamingData = decodeMeshletStreamUnits(bytes, meshletStreamEntry);
        MgiRayTracingData rayTracingData = decodeRayTracingRegions(bytes, rtRegionsEntry);
        MgiTessellationData tessellationData = decodeTessellationRegions(bytes, tessellationRegionsEntry);

        MgiStaticMesh mesh = new MgiStaticMesh(
            streams.positions,
            streams.normals,
            streams.uv0,
            bounds,
            metadata,
            meshletData,
            meshletLodData,
            meshletStreamingData,
            rayTracingData,
            tessellationData,
            indices,
            List.of(ranges)
        );
        MgiStaticMeshWriter.validateMesh(mesh);
        return mesh;
    }

    private static List<MgiStaticMeshWriter.AttributeSpec> decodeAttributeSchema(byte[] bytes, MgiChunkEntry entry) {
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
        ArrayList<MgiStaticMeshWriter.AttributeSpec> specs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int semantic = b.getInt();
            int components = b.getInt();
            int componentType = b.getInt();
            int strideBytes = b.getInt();
            MgiStaticMeshWriter.AttributeSpec spec = new MgiStaticMeshWriter.AttributeSpec(semantic, components, componentType, strideBytes);
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
        List<MgiStaticMeshWriter.AttributeSpec> specs,
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

        for (MgiStaticMeshWriter.AttributeSpec spec : specs) {
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

    static int[] decodeIntPayload(byte[] bytes, MgiChunkEntry entry, int expectedCount) {
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

    private static MgiMeshletData decodeMeshlets(
        byte[] bytes,
        MgiChunkEntry descriptorEntry,
        MgiChunkEntry remapEntry,
        MgiChunkEntry triangleEntry,
        MgiChunkEntry boundsEntry,
        int submeshCount
    ) {
        int present = 0;
        if (descriptorEntry != null) {
            present++;
        }
        if (remapEntry != null) {
            present++;
        }
        if (triangleEntry != null) {
            present++;
        }
        if (boundsEntry != null) {
            present++;
        }
        if (present == 0) {
            return null;
        }
        if (present != 4) {
            throw new MgiValidationException("meshlet chunk set must be complete when present");
        }

        int[] descriptorInts = decodeIntPayloadMultiple(bytes, descriptorEntry, MESHLET_DESCRIPTOR_INTS);
        int descriptorCount = descriptorInts.length / MESHLET_DESCRIPTOR_INTS;
        int[] remap = decodeIntPayloadAny(bytes, remapEntry);
        int[] triangles = decodeIntPayloadAny(bytes, triangleEntry);
        List<MgiMeshletBounds> bounds = decodeMeshletBounds(bytes, boundsEntry);

        ArrayList<MgiMeshletDescriptor> descriptors = new ArrayList<>(descriptorCount);
        for (int i = 0; i < descriptorCount; i++) {
            int base = i * MESHLET_DESCRIPTOR_INTS;
            MgiMeshletDescriptor descriptor = new MgiMeshletDescriptor(
                descriptorInts[base],
                descriptorInts[base + 1],
                descriptorInts[base + 2],
                descriptorInts[base + 3],
                descriptorInts[base + 4],
                descriptorInts[base + 5],
                descriptorInts[base + 6],
                descriptorInts[base + 7]
            );
            if (descriptor.submeshIndex() >= submeshCount) {
                throw new MgiValidationException("meshlet descriptor submesh index out of range");
            }
            descriptors.add(descriptor);
        }

        return new MgiMeshletData(descriptors, remap, triangles, bounds);
    }

    static int[] decodeIntPayloadAny(byte[] bytes, MgiChunkEntry entry) {
        if ((entry.lengthBytes() % Integer.BYTES) != 0) {
            throw new MgiValidationException("invalid int payload alignment");
        }
        int expectedCount = Math.toIntExact(entry.lengthBytes() / Integer.BYTES);
        return decodeIntPayload(bytes, entry, expectedCount);
    }

    static int[] decodeIntPayloadMultiple(byte[] bytes, MgiChunkEntry entry, int elementInts) {
        if ((entry.lengthBytes() % Integer.BYTES) != 0) {
            throw new MgiValidationException("invalid int payload alignment");
        }
        int count = Math.toIntExact(entry.lengthBytes() / Integer.BYTES);
        if ((count % elementInts) != 0) {
            throw new MgiValidationException("invalid int payload element size");
        }
        return decodeIntPayload(bytes, entry, count);
    }

    private static List<MgiMeshletBounds> decodeMeshletBounds(byte[] bytes, MgiChunkEntry entry) {
        if ((entry.lengthBytes() % Float.BYTES) != 0) {
            throw new MgiValidationException("meshlet bounds payload is not float-aligned");
        }
        int floatCount = Math.toIntExact(entry.lengthBytes() / Float.BYTES);
        if ((floatCount % MESHLET_BOUNDS_FLOATS) != 0) {
            throw new MgiValidationException("invalid meshlet bounds payload size");
        }
        int boundCount = floatCount / MESHLET_BOUNDS_FLOATS;
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);
        ArrayList<MgiMeshletBounds> out = new ArrayList<>(boundCount);
        for (int i = 0; i < boundCount; i++) {
            out.add(new MgiMeshletBounds(
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat(),
                b.getFloat()
            ));
        }
        return List.copyOf(out);
    }

    private static MgiMeshletLodData decodeMeshletLodLevels(byte[] bytes, MgiChunkEntry entry) {
        if (entry == null) {
            return null;
        }
        if ((entry.lengthBytes() % MESHLET_LOD_LEVEL_BYTES) != 0) {
            throw new MgiValidationException("invalid meshlet LOD payload size");
        }
        int levelCount = Math.toIntExact(entry.lengthBytes() / MESHLET_LOD_LEVEL_BYTES);
        if (levelCount == 0) {
            throw new MgiValidationException("meshlet LOD payload must not be empty");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);
        ArrayList<MgiMeshletLodLevel> levels = new ArrayList<>(levelCount);
        for (int i = 0; i < levelCount; i++) {
            levels.add(new MgiMeshletLodLevel(
                b.getInt(),
                b.getInt(),
                b.getInt(),
                b.getFloat()
            ));
        }
        return new MgiMeshletLodData(levels);
    }

    private static MgiMeshletStreamingData decodeMeshletStreamUnits(byte[] bytes, MgiChunkEntry entry) {
        if (entry == null) {
            return null;
        }
        int[] unitInts = decodeIntPayloadMultiple(bytes, entry, MESHLET_STREAM_UNIT_INTS);
        int unitCount = unitInts.length / MESHLET_STREAM_UNIT_INTS;
        if (unitCount == 0) {
            throw new MgiValidationException("meshlet stream unit payload must not be empty");
        }
        ArrayList<MgiMeshletStreamUnit> units = new ArrayList<>(unitCount);
        for (int i = 0; i < unitCount; i++) {
            int base = i * MESHLET_STREAM_UNIT_INTS;
            units.add(new MgiMeshletStreamUnit(
                unitInts[base],
                unitInts[base + 1],
                unitInts[base + 2],
                unitInts[base + 3],
                unitInts[base + 4]
            ));
        }
        return new MgiMeshletStreamingData(units);
    }

    private static MgiRayTracingData decodeRayTracingRegions(byte[] bytes, MgiChunkEntry entry) {
        if (entry == null) {
            return null;
        }
        int[] regionInts = decodeIntPayloadMultiple(bytes, entry, RT_REGION_INTS);
        int regionCount = regionInts.length / RT_REGION_INTS;
        if (regionCount == 0) {
            throw new MgiValidationException("ray tracing region payload must not be empty");
        }
        ArrayList<MgiRayTracingRegion> regions = new ArrayList<>(regionCount);
        for (int i = 0; i < regionCount; i++) {
            int base = i * RT_REGION_INTS;
            regions.add(new MgiRayTracingRegion(
                regionInts[base],
                regionInts[base + 1],
                regionInts[base + 2],
                regionInts[base + 3],
                regionInts[base + 4]
            ));
        }
        return new MgiRayTracingData(regions);
    }

    private static MgiTessellationData decodeTessellationRegions(byte[] bytes, MgiChunkEntry entry) {
        if (entry == null) {
            return null;
        }
        if ((entry.lengthBytes() % TESSELLATION_REGION_BYTES) != 0) {
            throw new MgiValidationException("invalid tessellation region payload size");
        }
        int regionCount = Math.toIntExact(entry.lengthBytes() / TESSELLATION_REGION_BYTES);
        if (regionCount == 0) {
            throw new MgiValidationException("tessellation region payload must not be empty");
        }
        ByteBuffer b = ByteBuffer.wrap(bytes, Math.toIntExact(entry.offsetBytes()), Math.toIntExact(entry.lengthBytes()))
            .order(ByteOrder.LITTLE_ENDIAN);
        ArrayList<MgiTessellationRegion> regions = new ArrayList<>(regionCount);
        for (int i = 0; i < regionCount; i++) {
            regions.add(new MgiTessellationRegion(
                b.getInt(),
                b.getInt(),
                b.getInt(),
                b.getInt(),
                b.getFloat(),
                b.getInt()
            ));
        }
        return new MgiTessellationData(regions);
    }

    static MgiChunkEntry required(Map<MgiChunkType, MgiChunkEntry> map, MgiChunkType type) {
        MgiChunkEntry entry = map.get(type);
        if (entry == null) {
            throw new MgiValidationException("missing required chunk: " + type);
        }
        return entry;
    }

    record DecodedStreams(float[] positions, float[] normals, float[] uv0) {
    }
}
