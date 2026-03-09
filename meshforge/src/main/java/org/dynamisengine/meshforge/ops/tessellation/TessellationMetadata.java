package org.dynamisengine.meshforge.ops.tessellation;

import java.util.List;

/**
 * Runtime handoff metadata describing tessellation/subdivision-relevant geometry regions.
 */
public record TessellationMetadata(List<TessellationRegionMetadata> regions) {
    public TessellationMetadata {
        if (regions == null) {
            throw new NullPointerException("regions");
        }
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("regions must not be empty");
        }
        regions = List.copyOf(regions);

        int previousSubmesh = -1;
        for (TessellationRegionMetadata region : regions) {
            if (region == null) {
                throw new NullPointerException("region");
            }
            if (region.submeshIndex() <= previousSubmesh) {
                throw new IllegalArgumentException("region submesh indexes must be strictly increasing");
            }
            previousSubmesh = region.submeshIndex();
        }
    }

    public int regionCount() {
        return regions.size();
    }
}
