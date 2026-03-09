package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MgiHeaderTest {

    @Test
    void rejectsInvalidMagic() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new MgiHeader(0xDEADBEEF, MgiVersion.V1_0, 0, 0, MgiConstants.HEADER_SIZE_BYTES, 0)
        );
    }

    @Test
    void rejectsInvalidOffsetsAndCounts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new MgiHeader(MgiConstants.MAGIC, MgiVersion.V1_0, 0, -1, MgiConstants.HEADER_SIZE_BYTES, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MgiHeader(MgiConstants.MAGIC, MgiVersion.V1_0, 0, 0, 8, 0)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new MgiHeader(MgiConstants.MAGIC, MgiVersion.V1_0, 0, 0, MgiConstants.HEADER_SIZE_BYTES, -1)
        );
    }

    @Test
    void createsValidV1Header() {
        MgiHeader header = MgiHeader.v1(3, 128, 2);
        assertEquals(MgiConstants.MAGIC, header.magic());
        assertEquals(3, header.chunkCount());
        assertEquals(128, header.chunkDirectoryOffsetBytes());
        assertEquals(2, header.meshCount());
    }
}
