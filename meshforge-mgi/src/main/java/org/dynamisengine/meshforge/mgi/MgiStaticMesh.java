package org.dynamisengine.meshforge.mgi;

import java.util.List;

/**
 * Minimal static mesh payload for MGI v1 geometry round-trip.
 *
 * @param positions xyz float triplets (length = vertexCount * 3)
 * @param normalsOrNull optional xyz normal triplets (length = vertexCount * 3)
 * @param uv0OrNull optional uv pairs (length = vertexCount * 2)
 * @param boundsOrNull optional prebaked bounds payload
 * @param canonicalMetadataOrNull optional canonical/trust metadata
 * @param meshletDataOrNull optional meshlet metadata payload
 * @param meshletLodDataOrNull optional meshlet LOD metadata payload
 * @param meshletStreamingDataOrNull optional meshlet streaming metadata payload
 * @param rayTracingDataOrNull optional RT-relevant geometry metadata payload
 * @param tessellationDataOrNull optional tessellation/subdivision metadata payload
 * @param indices triangle index buffer (uint32 domain)
 * @param submeshes submesh index ranges
 */
public record MgiStaticMesh(
    float[] positions,
    float[] normalsOrNull,
    float[] uv0OrNull,
    MgiAabb boundsOrNull,
    MgiCanonicalMetadata canonicalMetadataOrNull,
    MgiMeshletData meshletDataOrNull,
    MgiMeshletLodData meshletLodDataOrNull,
    MgiMeshletStreamingData meshletStreamingDataOrNull,
    MgiRayTracingData rayTracingDataOrNull,
    MgiTessellationData tessellationDataOrNull,
    int[] indices,
    List<MgiSubmeshRange> submeshes
) {
    public MgiStaticMesh {
        if (positions == null) {
            throw new NullPointerException("positions");
        }
        if (indices == null) {
            throw new NullPointerException("indices");
        }
        if (submeshes == null) {
            throw new NullPointerException("submeshes");
        }
        if ((positions.length % 3) != 0) {
            throw new IllegalArgumentException("positions length must be divisible by 3");
        }

        int vertexCount = positions.length / 3;
        if (normalsOrNull != null && normalsOrNull.length != positions.length) {
            throw new IllegalArgumentException("normals length must match positions length");
        }
        if (uv0OrNull != null && uv0OrNull.length != vertexCount * 2) {
            throw new IllegalArgumentException("uv0 length must be vertexCount * 2");
        }

        positions = positions.clone();
        normalsOrNull = normalsOrNull == null ? null : normalsOrNull.clone();
        uv0OrNull = uv0OrNull == null ? null : uv0OrNull.clone();
        boundsOrNull = boundsOrNull == null ? null : new MgiAabb(
            boundsOrNull.minX(),
            boundsOrNull.minY(),
            boundsOrNull.minZ(),
            boundsOrNull.maxX(),
            boundsOrNull.maxY(),
            boundsOrNull.maxZ()
        );
        canonicalMetadataOrNull = canonicalMetadataOrNull == null ? null : new MgiCanonicalMetadata(
            canonicalMetadataOrNull.canonicalVertexCount(),
            canonicalMetadataOrNull.canonicalIndexCount(),
            canonicalMetadataOrNull.flags()
        );
        meshletDataOrNull = meshletDataOrNull == null ? null : new MgiMeshletData(
            meshletDataOrNull.descriptors(),
            meshletDataOrNull.vertexRemap(),
            meshletDataOrNull.triangles(),
            meshletDataOrNull.bounds()
        );
        meshletLodDataOrNull = meshletLodDataOrNull == null ? null : new MgiMeshletLodData(
            meshletLodDataOrNull.levels()
        );
        meshletStreamingDataOrNull = meshletStreamingDataOrNull == null ? null : new MgiMeshletStreamingData(
            meshletStreamingDataOrNull.units()
        );
        rayTracingDataOrNull = rayTracingDataOrNull == null ? null : new MgiRayTracingData(
            rayTracingDataOrNull.regions()
        );
        tessellationDataOrNull = tessellationDataOrNull == null ? null : new MgiTessellationData(
            tessellationDataOrNull.regions()
        );
        indices = indices.clone();
        submeshes = List.copyOf(submeshes);
    }

    /**
     * Backward-compatible constructor for pre-RT MGI static mesh construction paths.
     */
    public MgiStaticMesh(
        float[] positions,
        float[] normalsOrNull,
        float[] uv0OrNull,
        MgiAabb boundsOrNull,
        MgiCanonicalMetadata canonicalMetadataOrNull,
        MgiMeshletData meshletDataOrNull,
        MgiMeshletLodData meshletLodDataOrNull,
        MgiMeshletStreamingData meshletStreamingDataOrNull,
        MgiRayTracingData rayTracingDataOrNull,
        int[] indices,
        List<MgiSubmeshRange> submeshes
    ) {
        this(
            positions,
            normalsOrNull,
            uv0OrNull,
            boundsOrNull,
            canonicalMetadataOrNull,
            meshletDataOrNull,
            meshletLodDataOrNull,
            meshletStreamingDataOrNull,
            rayTracingDataOrNull,
            null,
            indices,
            submeshes
        );
    }

    /**
     * Backward-compatible constructor for pre-RT and pre-tessellation MGI static mesh construction paths.
     */
    public MgiStaticMesh(
        float[] positions,
        float[] normalsOrNull,
        float[] uv0OrNull,
        MgiAabb boundsOrNull,
        MgiCanonicalMetadata canonicalMetadataOrNull,
        MgiMeshletData meshletDataOrNull,
        MgiMeshletLodData meshletLodDataOrNull,
        MgiMeshletStreamingData meshletStreamingDataOrNull,
        int[] indices,
        List<MgiSubmeshRange> submeshes
    ) {
        this(
            positions,
            normalsOrNull,
            uv0OrNull,
            boundsOrNull,
            canonicalMetadataOrNull,
            meshletDataOrNull,
            meshletLodDataOrNull,
            meshletStreamingDataOrNull,
            null,
            null,
            indices,
            submeshes
        );
    }

    public int vertexCount() {
        return positions.length / 3;
    }
}
