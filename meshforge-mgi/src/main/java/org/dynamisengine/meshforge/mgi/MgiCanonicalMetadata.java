package org.dynamisengine.meshforge.mgi;

/**
 * Canonical/trust metadata used by runtime fast-path gating.
 *
 * @param canonicalVertexCount canonical vertex count
 * @param canonicalIndexCount canonical index count
 * @param flags metadata flags bitset
 */
public record MgiCanonicalMetadata(
    int canonicalVertexCount,
    int canonicalIndexCount,
    int flags
) {
    public static final int FLAG_DEGENERATE_FREE = 1 << 0;
    public static final int FLAG_TRUSTED_CANONICAL = 1 << 1;

    public MgiCanonicalMetadata {
        if (canonicalVertexCount < 0) {
            throw new IllegalArgumentException("canonicalVertexCount must be >= 0");
        }
        if (canonicalIndexCount < 0) {
            throw new IllegalArgumentException("canonicalIndexCount must be >= 0");
        }
    }

    public boolean degenerateFree() {
        return (flags & FLAG_DEGENERATE_FREE) != 0;
    }

    public boolean trustedCanonical() {
        return (flags & FLAG_TRUSTED_CANONICAL) != 0;
    }
}
