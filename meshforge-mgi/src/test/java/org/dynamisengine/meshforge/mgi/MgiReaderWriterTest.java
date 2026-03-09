package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MgiReaderWriterTest {

    @Test
    void roundTripsHeaderAndChunkDirectory() throws Exception {
        MgiHeader header = MgiHeader.v1(2, MgiConstants.HEADER_SIZE_BYTES, 1);
        List<MgiChunkEntry> chunks = List.of(
            new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), 128, 64, 0),
            new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), 192, 32, 0)
        );

        MgiWriter writer = new MgiWriter();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeHeader(out, header);
        writer.writeChunkDirectory(out, chunks);

        byte[] bytes = out.toByteArray();
        MgiReader reader = new MgiReader();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);

        MgiHeader readHeader = reader.readHeader(in);
        List<MgiChunkEntry> readChunks = reader.readChunkDirectory(in, readHeader.chunkCount());

        assertEquals(header, readHeader);
        assertEquals(chunks, readChunks);
    }
}
