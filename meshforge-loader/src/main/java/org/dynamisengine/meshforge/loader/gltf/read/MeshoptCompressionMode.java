package org.dynamisengine.meshforge.loader.gltf.read;

import java.util.Locale;

/**
 * Public enum MeshoptCompressionMode.
 */
public enum MeshoptCompressionMode {
    ATTRIBUTES,
    TRIANGLES;

    /**
     * Returns fromString.
     * @param raw parameter value
     * @return resulting value
     */
    public static MeshoptCompressionMode fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("meshopt mode must not be blank");
        }
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
    }
}
