package org.dynamisengine.meshforge.mgi;

/**
 * MGI format version.
 *
 * @param major breaking format version
 * @param minor additive format version
 */
public record MgiVersion(int major, int minor) {
    public static final MgiVersion V1_0 = new MgiVersion(1, 0);

    public MgiVersion {
        if (major <= 0) {
            throw new IllegalArgumentException("major must be > 0");
        }
        if (minor < 0) {
            throw new IllegalArgumentException("minor must be >= 0");
        }
    }

    public boolean isCompatibleWith(MgiVersion minimum, MgiVersion maximumInclusive) {
        if (minimum == null || maximumInclusive == null) {
            throw new NullPointerException("minimum/maximumInclusive must not be null");
        }
        return compareTo(minimum) >= 0 && compareTo(maximumInclusive) <= 0;
    }

    public int compareTo(MgiVersion other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        int majorCmp = Integer.compare(this.major, other.major);
        return majorCmp != 0 ? majorCmp : Integer.compare(this.minor, other.minor);
    }
}
