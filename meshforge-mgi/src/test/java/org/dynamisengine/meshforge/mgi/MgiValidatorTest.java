package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MgiValidatorTest {

    @Test
    void acceptsStructurallyValidDirectory() {
        MgiHeader header = MgiHeader.v1(5, MgiConstants.HEADER_SIZE_BYTES, 1);
        List<MgiChunkEntry> chunks = List.of(
            new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), 256, 64, 0),
            new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), 320, 64, 0),
            new MgiChunkEntry(MgiChunkType.VERTEX_STREAMS.id(), 384, 512, 0),
            new MgiChunkEntry(MgiChunkType.INDEX_DATA.id(), 896, 128, 0),
            new MgiChunkEntry(MgiChunkType.SUBMESH_TABLE.id(), 1024, 64, 0)
        );

        assertDoesNotThrow(() -> MgiValidator.validate(header, chunks, 2048));
    }

    @Test
    void rejectsUnsupportedVersion() {
        MgiHeader header = new MgiHeader(
            MgiConstants.MAGIC,
            new MgiVersion(2, 0),
            MgiConstants.FLAG_LITTLE_ENDIAN,
            5,
            MgiConstants.HEADER_SIZE_BYTES,
            1
        );

        List<MgiChunkEntry> chunks = List.of(
            new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), 256, 64, 0),
            new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), 320, 64, 0),
            new MgiChunkEntry(MgiChunkType.VERTEX_STREAMS.id(), 384, 512, 0),
            new MgiChunkEntry(MgiChunkType.INDEX_DATA.id(), 896, 128, 0),
            new MgiChunkEntry(MgiChunkType.SUBMESH_TABLE.id(), 1024, 64, 0)
        );

        assertThrows(MgiValidationException.class, () -> MgiValidator.validate(header, chunks, 2048));
    }

    @Test
    void rejectsMissingLittleEndianFlag() {
        MgiHeader header = new MgiHeader(
            MgiConstants.MAGIC,
            MgiVersion.V1_0,
            0,
            5,
            MgiConstants.HEADER_SIZE_BYTES,
            1
        );

        List<MgiChunkEntry> chunks = List.of(
            new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), 256, 64, 0),
            new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), 320, 64, 0),
            new MgiChunkEntry(MgiChunkType.VERTEX_STREAMS.id(), 384, 512, 0),
            new MgiChunkEntry(MgiChunkType.INDEX_DATA.id(), 896, 128, 0),
            new MgiChunkEntry(MgiChunkType.SUBMESH_TABLE.id(), 1024, 64, 0)
        );

        assertThrows(MgiValidationException.class, () -> MgiValidator.validate(header, chunks, 2048));
    }

    @Test
    void rejectsMissingRequiredChunk() {
        MgiHeader header = MgiHeader.v1(4, MgiConstants.HEADER_SIZE_BYTES, 1);
        List<MgiChunkEntry> chunks = List.of(
            new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), 256, 64, 0),
            new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), 320, 64, 0),
            new MgiChunkEntry(MgiChunkType.VERTEX_STREAMS.id(), 384, 512, 0),
            new MgiChunkEntry(MgiChunkType.INDEX_DATA.id(), 896, 128, 0)
        );

        assertThrows(MgiValidationException.class, () -> MgiValidator.validate(header, chunks, 2048));
    }

    @Test
    void rejectsChunkPayloadOverlappingDirectory() {
        MgiHeader header = MgiHeader.v1(5, MgiConstants.HEADER_SIZE_BYTES, 1);
        // Directory ends at 32 + (5 * 32) = 192, first payload starts at 160.
        List<MgiChunkEntry> chunks = List.of(
            new MgiChunkEntry(MgiChunkType.MESH_TABLE.id(), 160, 64, 0),
            new MgiChunkEntry(MgiChunkType.ATTRIBUTE_SCHEMA.id(), 224, 64, 0),
            new MgiChunkEntry(MgiChunkType.VERTEX_STREAMS.id(), 288, 512, 0),
            new MgiChunkEntry(MgiChunkType.INDEX_DATA.id(), 800, 128, 0),
            new MgiChunkEntry(MgiChunkType.SUBMESH_TABLE.id(), 928, 64, 0)
        );

        assertThrows(MgiValidationException.class, () -> MgiValidator.validate(header, chunks, 2048));
    }
}
