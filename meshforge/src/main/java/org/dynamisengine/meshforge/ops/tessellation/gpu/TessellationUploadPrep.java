package org.dynamisengine.meshforge.ops.tessellation.gpu;

import org.dynamisengine.meshforge.ops.tessellation.TessellationMetadata;
import org.dynamisengine.meshforge.ops.tessellation.TessellationRegionMetadata;

import java.util.List;

/**
 * CPU-side preparation of tessellation metadata payload for future GPU-side tessellation/subdivision work.
 */
public final class TessellationUploadPrep {
    private TessellationUploadPrep() {
    }

    /**
     * Flattens tessellation regions into GPU-ready int32 payload layout.
     */
    public static GpuTessellationPayload fromMetadata(TessellationMetadata metadata) {
        if (metadata == null) {
            throw new NullPointerException("metadata");
        }

        List<TessellationRegionMetadata> regions = metadata.regions();
        int count = regions.size();
        int stride = GpuTessellationPayload.REGION_COMPONENTS;
        int[] payload = new int[count * stride];

        int at = 0;
        for (int i = 0; i < count; i++) {
            TessellationRegionMetadata region = regions.get(i);
            payload[at++] = region.submeshIndex();
            payload[at++] = region.firstIndex();
            payload[at++] = region.indexCount();
            payload[at++] = region.patchControlPoints();
            payload[at++] = Float.floatToRawIntBits(region.tessLevel());
            payload[at++] = region.flags();
        }

        return new GpuTessellationPayload(
            count,
            0,
            stride,
            payload
        );
    }
}
