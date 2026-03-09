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
    private static final int MESHLET_DESCRIPTOR_INTS = 8;
    private static final int MESHLET_BOUNDS_FLOATS = 6;
    private static final int MESHLET_LOD_LEVEL_BYTES = (3 * Integer.BYTES) + Float.BYTES;
    private static final int MESHLET_STREAM_UNIT_INTS = 5;
    private static final int RT_REGION_INTS = 5;
    private static final int TESSELLATION_REGION_BYTES = (5 * Integer.BYTES) + Float.BYTES;

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
        byte[] meshletDescriptorBytes = mesh.meshletDataOrNull() == null
            ? null
            : encodeMeshletDescriptors(mesh.meshletDataOrNull().descriptors());
        byte[] meshletRemapBytes = mesh.meshletDataOrNull() == null
            ? null
            : encodeIntArray(mesh.meshletDataOrNull().vertexRemap());
        byte[] meshletTriangleBytes = mesh.meshletDataOrNull() == null
            ? null
            : encodeIntArray(mesh.meshletDataOrNull().triangles());
        byte[] meshletBoundsBytes = mesh.meshletDataOrNull() == null
            ? null
            : encodeMeshletBounds(mesh.meshletDataOrNull().bounds());
        byte[] meshletLodLevelBytes = mesh.meshletLodDataOrNull() == null
            ? null
            : encodeMeshletLodLevels(mesh.meshletLodDataOrNull().levels());
        byte[] meshletStreamUnitBytes = mesh.meshletStreamingDataOrNull() == null
            ? null
            : encodeMeshletStreamUnits(mesh.meshletStreamingDataOrNull().units());
        byte[] rtRegionBytes = mesh.rayTracingDataOrNull() == null
            ? null
            : encodeRayTracingRegions(mesh.rayTracingDataOrNull().regions());
        byte[] tessellationRegionBytes = mesh.tessellationDataOrNull() == null
            ? null
            : encodeTessellationRegions(mesh.tessellationDataOrNull().regions());

        long directoryOffset = MgiConstants.HEADER_SIZE_BYTES;
        int chunkCount = 5;
        if (boundsBytes != null) {
            chunkCount++;
        }
        if (metadataBytes != null) {
            chunkCount++;
        }
        if (meshletDescriptorBytes != null) {
            chunkCount += 4;
        }
        if (meshletLodLevelBytes != null) {
            chunkCount++;
        }
        if (meshletStreamUnitBytes != null) {
            chunkCount++;
        }
        if (rtRegionBytes != null) {
            chunkCount++;
        }
        if (tessellationRegionBytes != null) {
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

        ArrayList<MgiChunkEntry> entries = new ArrayList<>(chunkCount);
        entries.add(meshTable);
        entries.add(attrSchema);
        entries.add(vertices);
        entries.add(indices);
        entries.add(submeshes);

        MgiChunkEntry bounds = null;
        if (boundsBytes != null) {
            bounds = new MgiChunkEntry(MgiChunkType.BOUNDS.id(), payloadOffset, boundsBytes.length, 0);
            entries.add(bounds);
            payloadOffset = bounds.endExclusive();
        }
        MgiChunkEntry metadata = null;
        if (metadataBytes != null) {
            metadata = new MgiChunkEntry(MgiChunkType.METADATA.id(), payloadOffset, metadataBytes.length, 0);
            entries.add(metadata);
            payloadOffset = metadata.endExclusive();
        }

        MgiChunkEntry meshletDescriptors = null;
        MgiChunkEntry meshletRemap = null;
        MgiChunkEntry meshletTriangles = null;
        MgiChunkEntry meshletBounds = null;
        if (meshletDescriptorBytes != null) {
            meshletDescriptors = new MgiChunkEntry(
                MgiChunkType.MESHLET_DESCRIPTORS.id(),
                payloadOffset,
                meshletDescriptorBytes.length,
                0
            );
            payloadOffset = meshletDescriptors.endExclusive();
            meshletRemap = new MgiChunkEntry(
                MgiChunkType.MESHLET_VERTEX_REMAP.id(),
                payloadOffset,
                meshletRemapBytes.length,
                0
            );
            payloadOffset = meshletRemap.endExclusive();
            meshletTriangles = new MgiChunkEntry(
                MgiChunkType.MESHLET_TRIANGLES.id(),
                payloadOffset,
                meshletTriangleBytes.length,
                0
            );
            payloadOffset = meshletTriangles.endExclusive();
            meshletBounds = new MgiChunkEntry(
                MgiChunkType.MESHLET_BOUNDS.id(),
                payloadOffset,
                meshletBoundsBytes.length,
                0
            );
            entries.add(meshletDescriptors);
            entries.add(meshletRemap);
            entries.add(meshletTriangles);
            entries.add(meshletBounds);
            payloadOffset = meshletBounds.endExclusive();
        }

        MgiChunkEntry meshletLodLevels = null;
        if (meshletLodLevelBytes != null) {
            meshletLodLevels = new MgiChunkEntry(
                MgiChunkType.MESHLET_LOD_LEVELS.id(),
                payloadOffset,
                meshletLodLevelBytes.length,
                0
            );
            entries.add(meshletLodLevels);
            payloadOffset = meshletLodLevels.endExclusive();
        }
        MgiChunkEntry meshletStreamUnits = null;
        if (meshletStreamUnitBytes != null) {
            meshletStreamUnits = new MgiChunkEntry(
                MgiChunkType.MESHLET_STREAM_UNITS.id(),
                payloadOffset,
                meshletStreamUnitBytes.length,
                0
            );
            entries.add(meshletStreamUnits);
            payloadOffset = meshletStreamUnits.endExclusive();
        }
        MgiChunkEntry rtRegions = null;
        if (rtRegionBytes != null) {
            rtRegions = new MgiChunkEntry(
                MgiChunkType.RAY_TRACING_REGIONS.id(),
                payloadOffset,
                rtRegionBytes.length,
                0
            );
            entries.add(rtRegions);
            payloadOffset = rtRegions.endExclusive();
        }
        MgiChunkEntry tessellationRegions = null;
        if (tessellationRegionBytes != null) {
            tessellationRegions = new MgiChunkEntry(
                MgiChunkType.TESSELLATION_REGIONS.id(),
                payloadOffset,
                tessellationRegionBytes.length,
                0
            );
            entries.add(tessellationRegions);
            payloadOffset = tessellationRegions.endExclusive();
        }
        MgiHeader header = MgiHeader.v1(entries.size(), directoryOffset, 1);
        long fileSize = payloadOffset;
        MgiValidator.validate(header, List.copyOf(entries), fileSize);

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
        if (meshletDescriptorBytes != null) {
            out.write(meshletDescriptorBytes);
            out.write(meshletRemapBytes);
            out.write(meshletTriangleBytes);
            out.write(meshletBoundsBytes);
        }
        if (meshletLodLevelBytes != null) {
            out.write(meshletLodLevelBytes);
        }
        if (meshletStreamUnitBytes != null) {
            out.write(meshletStreamUnitBytes);
        }
        if (rtRegionBytes != null) {
            out.write(rtRegionBytes);
        }
        if (tessellationRegionBytes != null) {
            out.write(tessellationRegionBytes);
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

    private static byte[] encodeIntArray(int[] values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) {
            b.putInt(value);
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

    private static byte[] encodeMeshletDescriptors(List<MgiMeshletDescriptor> descriptors) {
        ByteBuffer b = ByteBuffer.allocate(descriptors.size() * MESHLET_DESCRIPTOR_INTS * Integer.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (MgiMeshletDescriptor descriptor : descriptors) {
            b.putInt(descriptor.submeshIndex());
            b.putInt(descriptor.materialSlot());
            b.putInt(descriptor.vertexRemapOffset());
            b.putInt(descriptor.vertexCount());
            b.putInt(descriptor.triangleOffset());
            b.putInt(descriptor.triangleCount());
            b.putInt(descriptor.boundsIndex());
            b.putInt(descriptor.flags());
        }
        return b.array();
    }

    private static byte[] encodeMeshletBounds(List<MgiMeshletBounds> bounds) {
        ByteBuffer b = ByteBuffer.allocate(bounds.size() * MESHLET_BOUNDS_FLOATS * Float.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (MgiMeshletBounds meshletBounds : bounds) {
            b.putFloat(meshletBounds.minX());
            b.putFloat(meshletBounds.minY());
            b.putFloat(meshletBounds.minZ());
            b.putFloat(meshletBounds.maxX());
            b.putFloat(meshletBounds.maxY());
            b.putFloat(meshletBounds.maxZ());
        }
        return b.array();
    }

    private static byte[] encodeMeshletLodLevels(List<MgiMeshletLodLevel> levels) {
        ByteBuffer b = ByteBuffer.allocate(levels.size() * MESHLET_LOD_LEVEL_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (MgiMeshletLodLevel level : levels) {
            b.putInt(level.lodLevel());
            b.putInt(level.meshletStart());
            b.putInt(level.meshletCount());
            b.putFloat(level.geometricError());
        }
        return b.array();
    }

    private static byte[] encodeMeshletStreamUnits(List<MgiMeshletStreamUnit> units) {
        ByteBuffer b = ByteBuffer.allocate(units.size() * MESHLET_STREAM_UNIT_INTS * Integer.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (MgiMeshletStreamUnit unit : units) {
            b.putInt(unit.streamUnitId());
            b.putInt(unit.meshletStart());
            b.putInt(unit.meshletCount());
            b.putInt(unit.payloadByteOffset());
            b.putInt(unit.payloadByteSize());
        }
        return b.array();
    }

    private static byte[] encodeRayTracingRegions(List<MgiRayTracingRegion> regions) {
        ByteBuffer b = ByteBuffer.allocate(regions.size() * RT_REGION_INTS * Integer.BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (MgiRayTracingRegion region : regions) {
            b.putInt(region.submeshIndex());
            b.putInt(region.firstIndex());
            b.putInt(region.indexCount());
            b.putInt(region.materialSlot());
            b.putInt(region.flags());
        }
        return b.array();
    }

    private static byte[] encodeTessellationRegions(List<MgiTessellationRegion> regions) {
        ByteBuffer b = ByteBuffer.allocate(regions.size() * TESSELLATION_REGION_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN);
        for (MgiTessellationRegion region : regions) {
            b.putInt(region.submeshIndex());
            b.putInt(region.firstIndex());
            b.putInt(region.indexCount());
            b.putInt(region.patchControlPoints());
            b.putFloat(region.tessLevel());
            b.putInt(region.flags());
        }
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

    private static int[] decodeIntPayloadAny(byte[] bytes, MgiChunkEntry entry) {
        if ((entry.lengthBytes() % Integer.BYTES) != 0) {
            throw new MgiValidationException("invalid int payload alignment");
        }
        int expectedCount = Math.toIntExact(entry.lengthBytes() / Integer.BYTES);
        return decodeIntPayload(bytes, entry, expectedCount);
    }

    private static int[] decodeIntPayloadMultiple(byte[] bytes, MgiChunkEntry entry, int elementInts) {
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
        if (mesh.meshletLodDataOrNull() != null) {
            if (mesh.meshletDataOrNull() == null) {
                throw new MgiValidationException("meshlet LOD metadata requires meshlet descriptors");
            }
            int descriptorCount = mesh.meshletDataOrNull().descriptors().size();
            for (MgiMeshletLodLevel level : mesh.meshletLodDataOrNull().levels()) {
                long end = (long) level.meshletStart() + level.meshletCount();
                if (end > descriptorCount) {
                    throw new MgiValidationException("meshlet LOD level range exceeds descriptor count");
                }
            }
        }
        if (mesh.meshletStreamingDataOrNull() != null) {
            if (mesh.meshletDataOrNull() == null) {
                throw new MgiValidationException("meshlet streaming metadata requires meshlet descriptors");
            }
            int descriptorCount = mesh.meshletDataOrNull().descriptors().size();
            for (MgiMeshletStreamUnit unit : mesh.meshletStreamingDataOrNull().units()) {
                long end = (long) unit.meshletStart() + unit.meshletCount();
                if (end > descriptorCount) {
                    throw new MgiValidationException("meshlet streaming unit range exceeds descriptor count");
                }
            }
        }
        if (mesh.rayTracingDataOrNull() != null) {
            int submeshCount = mesh.submeshes().size();
            for (MgiRayTracingRegion region : mesh.rayTracingDataOrNull().regions()) {
                if (region.submeshIndex() >= submeshCount) {
                    throw new MgiValidationException("ray tracing region submesh index out of range");
                }
                long end = (long) region.firstIndex() + region.indexCount();
                if (end > totalIndexCount) {
                    throw new MgiValidationException("ray tracing region range exceeds index count");
                }
            }
        }
        if (mesh.tessellationDataOrNull() != null) {
            int submeshCount = mesh.submeshes().size();
            for (MgiTessellationRegion region : mesh.tessellationDataOrNull().regions()) {
                if (region.submeshIndex() >= submeshCount) {
                    throw new MgiValidationException("tessellation region submesh index out of range");
                }
                long end = (long) region.firstIndex() + region.indexCount();
                if (end > totalIndexCount) {
                    throw new MgiValidationException("tessellation region range exceeds index count");
                }
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
