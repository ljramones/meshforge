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

        long directorySize = (long) chunks.size() * MgiConstants.CHUNK_ENTRY_SIZE_BYTES;
        long directoryEnd = header.chunkDirectoryOffsetBytes() + directorySize;
        if (directoryEnd > fileSizeBytes) {
            throw new MgiValidationException("chunk directory exceeds file bounds");
        }

        EnumSet<MgiChunkType> seenKnown = EnumSet.noneOf(MgiChunkType.class);
        List<MgiChunkEntry> sorted = new ArrayList<>(chunks);
        sorted.sort((a, b) -> Long.compare(a.offsetBytes(), b.offsetBytes()));

        long prevEnd = 0L;
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
            if (known != null) {
                if (!seenKnown.add(known)) {
                    throw new MgiValidationException("duplicate known chunk type: " + known);
                }
            }
        }

        for (MgiChunkType required : MgiChunkType.values()) {
            if (required.required() && !seenKnown.contains(required)) {
                throw new MgiValidationException("missing required chunk: " + required);
            }
        }
    }
}
