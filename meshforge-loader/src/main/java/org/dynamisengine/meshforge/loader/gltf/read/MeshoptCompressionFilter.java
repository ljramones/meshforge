package org.dynamisengine.meshforge.loader.gltf.read;

import java.util.Locale;

/**
 * Public enum MeshoptCompressionFilter.
 */
public enum MeshoptCompressionFilter {
    NONE,
    OCTAHEDRAL;

    /**
     * Returns fromString.
     * @param raw parameter value
     * @return resulting value
     */
    public static MeshoptCompressionFilter fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
