package org.dynamisengine.meshforge.mgi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Package-private writer for MGI v1 static mesh geometry payloads.
 */
final class MgiStaticMeshWriter {
    static final int MESH_TABLE_ENTRY_INTS = 4;
    static final int SUBMESH_ENTRY_INTS = 4;
    static final int BOUNDS_FLOATS = 6;
    static final int METADATA_INTS = 4;
    static final int MESHLET_DESCRIPTOR_INTS = 8;
    static final int MESHLET_BOUNDS_FLOATS = 6;
    static final int MESHLET_LOD_LEVEL_BYTES = (3 * Integer.BYTES) + Float.BYTES;
    static final int MESHLET_STREAM_UNIT_INTS = 5;
    static final int RT_REGION_INTS = 5;
    static final int TESSELLATION_REGION_BYTES = (5 * Integer.BYTES) + Float.BYTES;

    static final int SEM_POSITION = 1;
    static final int SEM_NORMAL = 2;
    static final int SEM_UV0 = 3;

    static final int TYPE_FLOAT32 = 1;

    byte[] write(MgiStaticMesh mesh) throws IOException {
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

    static byte[] encodeMeshTable(MgiStaticMesh mesh) {
        ByteBuffer b = ByteBuffer.allocate(MESH_TABLE_ENTRY_INTS * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(mesh.vertexCount());
        b.putInt(mesh.indices().length);
        b.putInt(mesh.submeshes().size());
        b.putInt(0);
        return b.array();
    }

    static byte[] encodeAttributeSchema(MgiStaticMesh mesh) {
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

    static byte[] encodeVertexStreams(MgiStaticMesh mesh) {
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

    static byte[] encodeIndices(int[] indices) {
        ByteBuffer b = ByteBuffer.allocate(indices.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int idx : indices) {
            b.putInt(idx);
        }
        return b.array();
    }

    static byte[] encodeSubmeshes(List<MgiSubmeshRange> submeshes) {
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

    static byte[] encodeIntArray(int[] values) {
        ByteBuffer b = ByteBuffer.allocate(values.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) {
            b.putInt(value);
        }
        return b.array();
    }

    static byte[] encodeBounds(MgiAabb bounds) {
        ByteBuffer b = ByteBuffer.allocate(BOUNDS_FLOATS * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putFloat(bounds.minX());
        b.putFloat(bounds.minY());
        b.putFloat(bounds.minZ());
        b.putFloat(bounds.maxX());
        b.putFloat(bounds.maxY());
        b.putFloat(bounds.maxZ());
        return b.array();
    }

    static byte[] encodeMeshletDescriptors(List<MgiMeshletDescriptor> descriptors) {
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

    static byte[] encodeMeshletBounds(List<MgiMeshletBounds> bounds) {
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

    static byte[] encodeMeshletLodLevels(List<MgiMeshletLodLevel> levels) {
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

    static byte[] encodeMeshletStreamUnits(List<MgiMeshletStreamUnit> units) {
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

    static byte[] encodeRayTracingRegions(List<MgiRayTracingRegion> regions) {
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

    static byte[] encodeTessellationRegions(List<MgiTessellationRegion> regions) {
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

    static byte[] encodeMetadata(MgiCanonicalMetadata metadata) {
        ByteBuffer b = ByteBuffer.allocate(METADATA_INTS * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(metadata.canonicalVertexCount());
        b.putInt(metadata.canonicalIndexCount());
        b.putInt(metadata.flags());
        b.putInt(0);
        return b.array();
    }

    static ArrayList<AttributeSpec> attributeSpecsFor(MgiStaticMesh mesh) {
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

    static void validateMesh(MgiStaticMesh mesh) {
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

    static final class AttributeSpec {
        final int semanticId;
        final int components;
        final int componentTypeId;
        final int strideBytes;

        AttributeSpec(int semanticId, int components, int componentTypeId, int strideBytes) {
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
}
