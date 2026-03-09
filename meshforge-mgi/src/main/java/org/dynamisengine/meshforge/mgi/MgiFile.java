package org.dynamisengine.meshforge.mgi;

import java.util.List;

/**
 * Minimal MGI file model for header + chunk directory scaffolding.
 *
 * @param header file header
 * @param chunks chunk-directory entries
 */
public record MgiFile(MgiHeader header, List<MgiChunkEntry> chunks) {
    public MgiFile {
        if (header == null) {
            throw new NullPointerException("header");
        }
        if (chunks == null) {
            throw new NullPointerException("chunks");
        }
        chunks = List.copyOf(chunks);
        if (header.chunkCount() != chunks.size()) {
            throw new IllegalArgumentException("header chunkCount/chunks size mismatch");
        }
    }
}
