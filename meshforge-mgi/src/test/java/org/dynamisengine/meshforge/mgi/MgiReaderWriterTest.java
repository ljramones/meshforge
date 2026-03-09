package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MgiReaderWriterTest {

    @Test
    void roundTripsHeaderAndChunkDirectoryViaFileModel() throws Exception {
        long payloadStart = MgiConstants.HEADER_SIZE_BYTES + (5L * MgiConstants.CHUNK_ENTRY_SIZE_BYTES);
        List<MgiChunkEntry> chunks = List.of(
            new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), payloadStart, 0, 0),
            new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), payloadStart, 0, 0),
            new MgiChunkEntry(MgiChunkType.VERTEX_STREAMS.id(), payloadStart, 0, 0),
            new MgiChunkEntry(MgiChunkType.INDEX_DATA.id(), payloadStart, 0, 0),
            new MgiChunkEntry(MgiChunkType.SUBMESH_TABLE.id(), payloadStart, 0, 0)
        );

        MgiHeader header = MgiHeader.v1(chunks.size(), MgiConstants.HEADER_SIZE_BYTES, 1);
        MgiFile file = new MgiFile(header, chunks);

        MgiWriter writer = new MgiWriter();
        byte[] bytes = writer.write(file);

        MgiReader reader = new MgiReader();
        MgiFile read = reader.read(bytes);

        assertEquals(file.header(), read.header());
        assertEquals(file.chunks(), read.chunks());
    }
}
