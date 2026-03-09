package org.dynamisengine.meshforge.mgi;

/**
 * Chunk directory entry.
 *
 * @param type chunk type id
 * @param offsetBytes absolute file offset
 * @param lengthBytes chunk payload length
 * @param flags chunk flags (reserved)
 */
public record MgiChunkEntry(int type, long offsetBytes, long lengthBytes, int flags) {
    public MgiChunkEntry {
        if (offsetBytes < 0L) {
            throw new IllegalArgumentException("offsetBytes must be >= 0");
        }
        if (lengthBytes < 0L) {
            throw new IllegalArgumentException("lengthBytes must be >= 0");
        }
    }

    public long endExclusive() {
        return offsetBytes + lengthBytes;
    }
}
