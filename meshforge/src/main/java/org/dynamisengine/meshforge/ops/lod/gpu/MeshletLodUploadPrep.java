package org.dynamisengine.meshforge.ops.lod.gpu;

import org.dynamisengine.meshforge.ops.lod.MeshletLodLevelMetadata;
import org.dynamisengine.meshforge.ops.lod.MeshletLodMetadata;

import java.util.List;

/**
 * CPU-side preparation of meshlet LOD metadata payload for future GPU-side LOD selection.
 * <p>
 * This is a handoff seam only; it does not perform GPU upload or selection policy.
 */
public final class MeshletLodUploadPrep {
    private MeshletLodUploadPrep() {
    }

    /**
     * Flattens meshlet LOD levels into GPU-ready int32 payload layout.
     *
     * Per level order:
     * - lodLevel
     * - meshletStart
     * - meshletCount
     * - geometricErrorBits ({@code Float.floatToRawIntBits(geometricError)})
     */
    public static GpuMeshletLodPayload fromMetadata(MeshletLodMetadata metadata) {
        if (metadata == null) {
            throw new NullPointerException("metadata");
        }

        List<MeshletLodLevelMetadata> levels = metadata.levels();
        int count = levels.size();
        int stride = GpuMeshletLodPayload.LEVEL_COMPONENTS;
        int[] payload = new int[count * stride];

        int at = 0;
        for (int i = 0; i < count; i++) {
            MeshletLodLevelMetadata level = levels.get(i);
            payload[at++] = level.lodLevel();
            payload[at++] = level.meshletStart();
            payload[at++] = level.meshletCount();
            payload[at++] = Float.floatToRawIntBits(level.geometricError());
        }

        return new GpuMeshletLodPayload(
            count,
            0,
            stride,
            payload
        );
    }
}
