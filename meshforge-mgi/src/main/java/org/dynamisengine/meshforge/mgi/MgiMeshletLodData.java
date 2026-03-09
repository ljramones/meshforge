package org.dynamisengine.meshforge.mgi;

import java.util.List;

/**
 * Optional meshlet LOD metadata payload.
 *
 * @param levels ordered LOD level ranges
 */
public record MgiMeshletLodData(List<MgiMeshletLodLevel> levels) {
    public MgiMeshletLodData {
        if (levels == null) {
            throw new NullPointerException("levels");
        }
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("levels must not be empty");
        }
        levels = List.copyOf(levels);

        int previousLevel = -1;
        long previousEnd = -1;
        for (MgiMeshletLodLevel level : levels) {
            if (level == null) {
                throw new NullPointerException("level");
            }
            if (level.lodLevel() <= previousLevel) {
                throw new IllegalArgumentException("lod levels must be strictly increasing");
            }
            long start = level.meshletStart();
            long end = start + level.meshletCount();
            if (start < previousEnd) {
                throw new IllegalArgumentException("lod meshlet ranges must be non-overlapping and ordered");
            }
            previousLevel = level.lodLevel();
            previousEnd = end;
        }
    }
}

