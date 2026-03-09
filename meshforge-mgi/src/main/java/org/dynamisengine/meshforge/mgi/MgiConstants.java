package org.dynamisengine.meshforge.mgi;

/**
 * MGI file-format constants.
 */
public final class MgiConstants {
    public static final int MAGIC = 0x3149474D; // "MGI1" little-endian marker in LE read/write flow.
    public static final int HEADER_SIZE_BYTES = 36;
    public static final int CHUNK_ENTRY_SIZE_BYTES = 32;

    public static final int FLAG_LITTLE_ENDIAN = 1 << 0;

    public static final MgiVersion MIN_SUPPORTED_VERSION = MgiVersion.V1_0;
    public static final MgiVersion MAX_SUPPORTED_VERSION = MgiVersion.V1_0;

    private MgiConstants() {
    }
}
