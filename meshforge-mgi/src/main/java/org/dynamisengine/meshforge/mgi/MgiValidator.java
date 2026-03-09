package org.dynamisengine.meshforge.mgi;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Structural validator for MGI header and chunk directory.
 */
public final class MgiValidator {
    private MgiValidator() {
    }

    public static void validate(MgiHeader header, List<MgiChunkEntry> chunks, long fileSizeBytes) {
        if (header == null) {
            throw new MgiValidationException("header is null");
        }
        if (chunks == null) {
            throw new MgiValidationException("chunks is null");
        }
        if (fileSizeBytes < MgiConstants.HEADER_SIZE_BYTES) {
            throw new MgiValidationException("file too small");
        }
        if (header.chunkCount() != chunks.size()) {
            throw new MgiValidationException("chunkCount/header mismatch");
        }
        if (!header.version().isCompatibleWith(MgiConstants.MIN_SUPPORTED_VERSION, MgiConstants.MAX_SUPPORTED_VERSION)) {
            throw new MgiValidationException(
                "unsupported version: " + header.version().major() + "." + header.version().minor()
            );
        }
        if ((header.flags() & MgiConstants.FLAG_LITTLE_ENDIAN) == 0) {
            throw new MgiValidationException("missing little-endian flag");
        }
        if (header.chunkDirectoryOffsetBytes() < MgiConstants.HEADER_SIZE_BYTES) {
            throw new MgiValidationException("chunk directory offset before header end");
        }

        long directorySize = (long) chunks.size() * MgiConstants.CHUNK_ENTRY_SIZE_BYTES;
        long directoryEnd = header.chunkDirectoryOffsetBytes() + directorySize;
        if (directoryEnd < header.chunkDirectoryOffsetBytes()) {
            throw new MgiValidationException("chunk directory offset overflow");
        }
        if (directoryEnd > fileSizeBytes) {
            throw new MgiValidationException("chunk directory exceeds file bounds");
        }

        EnumSet<MgiChunkType> seenKnown = EnumSet.noneOf(MgiChunkType.class);
        List<MgiChunkEntry> sorted = new ArrayList<>(chunks);
        sorted.sort((a, b) -> Long.compare(a.offsetBytes(), b.offsetBytes()));

        long prevEnd = directoryEnd;
        for (MgiChunkEntry entry : sorted) {
            if (entry.endExclusive() < entry.offsetBytes()) {
                throw new MgiValidationException("chunk overflow detected");
            }
            if (entry.endExclusive() > fileSizeBytes) {
                throw new MgiValidationException("chunk exceeds file bounds");
            }
            if (entry.offsetBytes() < prevEnd) {
                throw new MgiValidationException("chunk payload overlap detected");
            }
            prevEnd = entry.endExclusive();

            MgiChunkType known = MgiChunkType.fromId(entry.type());
            if (known != null && !seenKnown.add(known)) {
                throw new MgiValidationException("duplicate known chunk type: " + known);
            }
        }

        for (MgiChunkType required : MgiChunkType.values()) {
            if (required.required() && !seenKnown.contains(required)) {
                throw new MgiValidationException("missing required chunk: " + required);
            }
        }
    }
}
