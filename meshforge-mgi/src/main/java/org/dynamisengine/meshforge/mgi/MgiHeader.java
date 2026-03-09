package org.dynamisengine.meshforge.mgi;

/**
 * MGI file header.
 *
 * @param magic format magic
 * @param version format version
 * @param flags header flags
 * @param chunkCount chunk-directory entry count
 * @param chunkDirectoryOffsetBytes absolute file offset to chunk directory
 * @param meshCount number of meshes in file
 */
public record MgiHeader(
    int magic,
    MgiVersion version,
    int flags,
    int chunkCount,
    long chunkDirectoryOffsetBytes,
    int meshCount
) {
    public MgiHeader {
        if (magic != MgiConstants.MAGIC) {
            throw new IllegalArgumentException("invalid magic");
        }
        if (version == null) {
            throw new NullPointerException("version");
        }
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount must be >= 0");
        }
        if (chunkDirectoryOffsetBytes < MgiConstants.HEADER_SIZE_BYTES) {
            throw new IllegalArgumentException("chunkDirectoryOffsetBytes before header");
        }
        if (meshCount < 0) {
            throw new IllegalArgumentException("meshCount must be >= 0");
        }
    }

    public static MgiHeader v1(int chunkCount, long chunkDirectoryOffsetBytes, int meshCount) {
        return new MgiHeader(
            MgiConstants.MAGIC,
            MgiVersion.V1_0,
            MgiConstants.FLAG_LITTLE_ENDIAN,
            chunkCount,
            chunkDirectoryOffsetBytes,
            meshCount
        );
    }
}
