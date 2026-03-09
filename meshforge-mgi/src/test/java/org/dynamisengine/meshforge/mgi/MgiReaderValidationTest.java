package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MgiReaderValidationTest {

    @Test
    void rejectsInvalidMagicOnRead() throws Exception {
        byte[] bytes = validBytes();
        bytes[0] = (byte) 0x00;

        MgiReader reader = new MgiReader();
        assertThrows(IllegalArgumentException.class, () -> reader.read(bytes));
    }

    @Test
    void rejectsUnsupportedVersionOnRead() throws Exception {
        byte[] bytes = validBytes();
        // major version at byte offset 4.
        bytes[4] = 0x02;
        bytes[5] = 0x00;
        bytes[6] = 0x00;
        bytes[7] = 0x00;

        MgiReader reader = new MgiReader();
        assertThrows(MgiValidationException.class, () -> reader.read(bytes));
    }

    @Test
    void rejectsDirectoryOffsetBeforeHeaderOnRead() throws Exception {
        byte[] bytes = validBytes();
        // chunkDirectoryOffset at byte offset 20 (little-endian long).
        bytes[20] = 0x08;
        bytes[21] = 0x00;
        bytes[22] = 0x00;
        bytes[23] = 0x00;
        bytes[24] = 0x00;
        bytes[25] = 0x00;
        bytes[26] = 0x00;
        bytes[27] = 0x00;

        MgiReader reader = new MgiReader();
        assertThrows(IllegalArgumentException.class, () -> reader.read(bytes));
    }

    private static byte[] validBytes() throws Exception {
        long payloadStart = MgiConstants.HEADER_SIZE_BYTES + (5L * MgiConstants.CHUNK_ENTRY_SIZE_BYTES);
        List<MgiChunkEntry> chunks = List.of(
            new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), payloadStart, 0, 0),
            new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), payloadStart, 0, 0),
            new MgiChunkEntry(MgiChunkType.VERTEX_STREAMS.id(), payloadStart, 0, 0),
            new MgiChunkEntry(MgiChunkType.INDEX_DATA.id(), payloadStart, 0, 0),
            new MgiChunkEntry(MgiChunkType.SUBMESH_TABLE.id(), payloadStart, 0, 0)
        );

        MgiFile file = new MgiFile(MgiHeader.v1(chunks.size(), MgiConstants.HEADER_SIZE_BYTES, 1), chunks);
        return new MgiWriter().write(file);
    }
}
