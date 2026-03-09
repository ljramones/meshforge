package org.dynamisengine.meshforge.mgi;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Minimal MGI writer skeleton (header + chunk directory only).
 */
public final class MgiWriter {
    public void writeHeader(OutputStream output, MgiHeader header) throws IOException {
        if (output == null) {
            throw new NullPointerException("output");
        }
        if (header == null) {
            throw new NullPointerException("header");
        }
        MgiIo.writeIntLe(output, header.magic());
        MgiIo.writeIntLe(output, header.version().major());
        MgiIo.writeIntLe(output, header.version().minor());
        MgiIo.writeIntLe(output, header.flags());
        MgiIo.writeIntLe(output, header.chunkCount());
        MgiIo.writeLongLe(output, header.chunkDirectoryOffsetBytes());
        MgiIo.writeIntLe(output, header.meshCount());
        MgiIo.writeIntLe(output, 0); // reserved
    }

    public void writeChunkDirectory(OutputStream output, List<MgiChunkEntry> chunks) throws IOException {
        if (output == null) {
            throw new NullPointerException("output");
        }
        if (chunks == null) {
            throw new NullPointerException("chunks");
        }
        for (MgiChunkEntry chunk : chunks) {
            MgiIo.writeIntLe(output, chunk.type());
            MgiIo.writeIntLe(output, chunk.flags());
            MgiIo.writeLongLe(output, chunk.offsetBytes());
            MgiIo.writeLongLe(output, chunk.lengthBytes());
            MgiIo.writeLongLe(output, 0L); // reserved
        }
    }
}
