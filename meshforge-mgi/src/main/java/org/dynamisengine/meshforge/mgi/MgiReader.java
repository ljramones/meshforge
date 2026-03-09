package org.dynamisengine.meshforge.mgi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal MGI reader skeleton (header + chunk directory only).
 */
public final class MgiReader {
    public MgiFile read(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        MgiHeader header = readHeader(in);

        long currentOffset = MgiConstants.HEADER_SIZE_BYTES;
        long targetOffset = header.chunkDirectoryOffsetBytes();
        if (targetOffset > currentOffset) {
            MgiIo.skipFully(in, targetOffset - currentOffset);
        }

        List<MgiChunkEntry> chunks = readChunkDirectory(in, header.chunkCount());
        MgiValidator.validate(header, chunks, bytes.length);
        return new MgiFile(header, chunks);
    }

    public MgiHeader readHeader(InputStream input) throws IOException {
        if (input == null) {
            throw new NullPointerException("input");
        }
        int magic = MgiIo.readIntLe(input);
        int major = MgiIo.readIntLe(input);
        int minor = MgiIo.readIntLe(input);
        int flags = MgiIo.readIntLe(input);
        int chunkCount = MgiIo.readIntLe(input);
        long chunkDirectoryOffsetBytes = MgiIo.readLongLe(input);
        int meshCount = MgiIo.readIntLe(input);
        MgiIo.readIntLe(input); // reserved

        return new MgiHeader(
            magic,
            new MgiVersion(major, minor),
            flags,
            chunkCount,
            chunkDirectoryOffsetBytes,
            meshCount
        );
    }

    public List<MgiChunkEntry> readChunkDirectory(InputStream input, int chunkCount) throws IOException {
        if (input == null) {
            throw new NullPointerException("input");
        }
        if (chunkCount < 0) {
            throw new IllegalArgumentException("chunkCount must be >= 0");
        }

        ArrayList<MgiChunkEntry> entries = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            int type = MgiIo.readIntLe(input);
            int flags = MgiIo.readIntLe(input);
            long offset = MgiIo.readLongLe(input);
            long length = MgiIo.readLongLe(input);
            MgiIo.readLongLe(input); // reserved
            entries.add(new MgiChunkEntry(type, offset, length, flags));
        }
        return List.copyOf(entries);
    }
}
