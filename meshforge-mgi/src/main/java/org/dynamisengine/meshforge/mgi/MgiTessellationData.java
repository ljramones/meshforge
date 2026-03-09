package org.dynamisengine.meshforge.mgi;

import java.util.List;

/**
 * Optional tessellation/subdivision metadata payload for MGI static meshes.
 */
public record MgiTessellationData(List<MgiTessellationRegion> regions) {
    public MgiTessellationData {
        if (regions == null) {
            throw new NullPointerException("regions");
        }
        if (regions.isEmpty()) {
            throw new IllegalArgumentException("regions must not be empty");
        }
        regions = List.copyOf(regions);

        int previousSubmesh = -1;
        for (MgiTessellationRegion region : regions) {
            if (region == null) {
                throw new NullPointerException("region");
            }
            if (region.submeshIndex() <= previousSubmesh) {
                throw new IllegalArgumentException("tessellation region submesh indexes must be strictly increasing");
            }
            previousSubmesh = region.submeshIndex();
        }
    }
}

