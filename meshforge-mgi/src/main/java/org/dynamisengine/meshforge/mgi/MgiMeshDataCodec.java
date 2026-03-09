package org.dynamisengine.meshforge.mgi;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.bounds.Aabbf;
import org.dynamisengine.meshforge.core.bounds.Boundsf;
import org.dynamisengine.meshforge.core.bounds.Spheref;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter between canonical MeshForge {@link MeshData} and MGI static mesh payload bytes.
 */
public final class MgiMeshDataCodec {
    private final MgiStaticMeshCodec staticMeshCodec = new MgiStaticMeshCodec();

    public byte[] write(MeshData meshData) throws IOException {
        if (meshData == null) {
            throw new NullPointerException("meshData");
        }
        return staticMeshCodec.write(toMgiStaticMesh(meshData));
    }

    public MeshData read(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        return toMeshData(staticMeshCodec.read(bytes));
    }

    public static MgiStaticMesh toMgiStaticMesh(MeshData meshData) {
        if (meshData == null) {
            throw new NullPointerException("meshData");
        }
        if (meshData.topology() != Topology.TRIANGLES) {
            throw new IllegalArgumentException("MGI v1 static mesh codec requires TRIANGLES topology");
        }

        VertexAttributeView positions = meshData.attribute(AttributeSemantic.POSITION, 0);
        if (positions.format() != VertexFormat.F32x3) {
            throw new IllegalArgumentException("MGI v1 static mesh codec requires POSITION[0] format F32x3");
        }

        VertexAttributeView normals = optional(meshData, AttributeSemantic.NORMAL, 0);
        if (normals != null && normals.format() != VertexFormat.F32x3) {
            throw new IllegalArgumentException("MGI v1 static mesh codec supports NORMAL[0] format F32x3 only");
        }

        VertexAttributeView uv0 = optional(meshData, AttributeSemantic.UV, 0);
        if (uv0 != null && uv0.format() != VertexFormat.F32x2) {
            throw new IllegalArgumentException("MGI v1 static mesh codec supports UV[0] format F32x2 only");
        }

        int[] indices = meshData.indicesOrNull();
        if (indices == null) {
            throw new IllegalArgumentException("MGI v1 static mesh codec requires indexed geometry");
        }

        float[] packedPositions = extractF32(positions, 3);
        float[] packedNormals = normals == null ? null : extractF32(normals, 3);
        float[] packedUv0 = uv0 == null ? null : extractF32(uv0, 2);
        List<MgiSubmeshRange> ranges = convertSubmeshes(meshData.submeshes(), indices.length);
        MgiAabb bounds = toMgiAabb(meshData);
        MgiCanonicalMetadata metadata = canonicalMetadata(meshData, packedPositions);
        return new MgiStaticMesh(packedPositions, packedNormals, packedUv0, bounds, metadata, indices, ranges);
    }

    public static MeshData toMeshData(MgiStaticMesh mesh) {
        if (mesh == null) {
            throw new NullPointerException("mesh");
        }

        VertexSchema.Builder schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3);
        if (mesh.normalsOrNull() != null) {
            schema.add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3);
        }
        if (mesh.uv0OrNull() != null) {
            schema.add(AttributeSemantic.UV, 0, VertexFormat.F32x2);
        }

        List<Submesh> submeshes = new ArrayList<>(mesh.submeshes().size());
        for (MgiSubmeshRange range : mesh.submeshes()) {
            submeshes.add(new Submesh(range.firstIndex(), range.indexCount(), Integer.valueOf(range.materialSlot())));
        }

        MeshData data = new MeshData(
            Topology.TRIANGLES,
            schema.build(),
            mesh.vertexCount(),
            mesh.indices(),
            submeshes
        );

        writeAttribute(data.attribute(AttributeSemantic.POSITION, 0), mesh.positions(), 3);
        if (mesh.normalsOrNull() != null) {
            writeAttribute(data.attribute(AttributeSemantic.NORMAL, 0), mesh.normalsOrNull(), 3);
        }
        if (mesh.uv0OrNull() != null) {
            writeAttribute(data.attribute(AttributeSemantic.UV, 0), mesh.uv0OrNull(), 2);
        }
        if (mesh.boundsOrNull() != null) {
            data.setBounds(toBoundsf(mesh.boundsOrNull()));
        }
        return data;
    }

    private static VertexAttributeView optional(MeshData meshData, AttributeSemantic semantic, int setIndex) {
        return meshData.has(semantic, setIndex) ? meshData.attribute(semantic, setIndex) : null;
    }

    private static float[] extractF32(VertexAttributeView view, int components) {
        float[] raw = view.rawFloatArrayOrNull();
        if (raw != null) {
            return raw.clone();
        }

        int vertexCount = view.vertexCount();
        float[] packed = new float[vertexCount * components];
        int at = 0;
        for (int i = 0; i < vertexCount; i++) {
            for (int c = 0; c < components; c++) {
                packed[at++] = view.getFloat(i, c);
            }
        }
        return packed;
    }

    private static void writeAttribute(VertexAttributeView view, float[] values, int components) {
        float[] raw = view.rawFloatArrayOrNull();
        if (raw != null) {
            System.arraycopy(values, 0, raw, 0, values.length);
            return;
        }

        int at = 0;
        for (int i = 0; i < view.vertexCount(); i++) {
            for (int c = 0; c < components; c++) {
                view.setFloat(i, c, values[at++]);
            }
        }
    }

    private static List<MgiSubmeshRange> convertSubmeshes(List<Submesh> source, int indexCount) {
        if (source.isEmpty()) {
            return List.of(new MgiSubmeshRange(0, indexCount, 0));
        }

        Map<Object, Integer> materialSlots = new LinkedHashMap<>();
        ArrayList<MgiSubmeshRange> out = new ArrayList<>(source.size());
        for (Submesh submesh : source) {
            int slot = materialSlot(submesh.materialId(), materialSlots);
            long end = (long) submesh.firstIndex() + submesh.indexCount();
            if (submesh.firstIndex() < 0 || submesh.indexCount() < 0 || end > indexCount) {
                throw new IllegalArgumentException("Submesh range exceeds index buffer: first=" + submesh.firstIndex()
                    + ", count=" + submesh.indexCount() + ", indexCount=" + indexCount);
            }
            out.add(new MgiSubmeshRange(submesh.firstIndex(), submesh.indexCount(), slot));
        }
        return List.copyOf(out);
    }

    private static int materialSlot(Object materialId, Map<Object, Integer> materialSlots) {
        if (materialId == null) {
            return 0;
        }
        if (materialId instanceof Number number) {
            int slot = number.intValue();
            if (slot < 0) {
                throw new IllegalArgumentException("material slot must be >= 0");
            }
            return slot;
        }
        Integer existing = materialSlots.get(materialId);
        if (existing != null) {
            return existing;
        }
        int created = materialSlots.size() + 1;
        materialSlots.put(materialId, created);
        return created;
    }

    private static MgiAabb toMgiAabb(MeshData meshData) {
        Boundsf bounds = meshData.boundsOrNull();
        if (bounds == null || bounds.aabb() == null) {
            return null;
        }
        Aabbf aabb = bounds.aabb();
        return new MgiAabb(aabb.minX(), aabb.minY(), aabb.minZ(), aabb.maxX(), aabb.maxY(), aabb.maxZ());
    }

    private static Boundsf toBoundsf(MgiAabb aabb) {
        float centerX = (aabb.minX() + aabb.maxX()) * 0.5f;
        float centerY = (aabb.minY() + aabb.maxY()) * 0.5f;
        float centerZ = (aabb.minZ() + aabb.maxZ()) * 0.5f;
        float dx = aabb.maxX() - centerX;
        float dy = aabb.maxY() - centerY;
        float dz = aabb.maxZ() - centerZ;
        float radius = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new Boundsf(
            new Aabbf(aabb.minX(), aabb.minY(), aabb.minZ(), aabb.maxX(), aabb.maxY(), aabb.maxZ()),
            new Spheref(centerX, centerY, centerZ, radius)
        );
    }

    private static MgiCanonicalMetadata canonicalMetadata(MeshData meshData, float[] packedPositions) {
        int[] indices = meshData.indicesOrNull();
        int vertexCount = packedPositions.length / 3;
        int indexCount = indices == null ? 0 : indices.length;
        int flags = 0;
        if (isDegenerateFree(indices, packedPositions)) {
            flags |= MgiCanonicalMetadata.FLAG_DEGENERATE_FREE;
        }
        if ((flags & MgiCanonicalMetadata.FLAG_DEGENERATE_FREE) != 0 && meshData.boundsOrNull() != null) {
            flags |= MgiCanonicalMetadata.FLAG_TRUSTED_CANONICAL;
        }
        return new MgiCanonicalMetadata(vertexCount, indexCount, flags);
    }

    private static boolean isDegenerateFree(int[] indices, float[] positions) {
        if (indices == null || indices.length < 3) {
            return true;
        }
        for (int i = 0; i < indices.length; i += 3) {
            int a = indices[i];
            int b = indices[i + 1];
            int c = indices[i + 2];
            if (a == b || b == c || a == c) {
                return false;
            }
            if (isZeroArea(a, b, c, positions)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isZeroArea(int ia, int ib, int ic, float[] pos) {
        int a = ia * 3;
        int b = ib * 3;
        int c = ic * 3;

        float ax = pos[a];
        float ay = pos[a + 1];
        float az = pos[a + 2];
        float bx = pos[b];
        float by = pos[b + 1];
        float bz = pos[b + 2];
        float cx = pos[c];
        float cy = pos[c + 1];
        float cz = pos[c + 2];

        float abx = bx - ax;
        float aby = by - ay;
        float abz = bz - az;
        float acx = cx - ax;
        float acy = cy - ay;
        float acz = cz - az;

        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;
        float area2 = nx * nx + ny * ny + nz * nz;
        return area2 <= 1.0e-20f;
    }
}
