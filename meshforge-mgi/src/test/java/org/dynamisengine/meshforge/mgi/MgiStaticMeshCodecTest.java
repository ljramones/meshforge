package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MgiStaticMeshCodecTest {

    @Test
    void roundTripsMinimalStaticMeshGeometry() throws Exception {
        MgiStaticMesh input = new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f,
                1f, 1f, 0f
            },
            null,
            null,
            null,
            null,
            new int[] {0, 1, 2, 1, 3, 2},
            List.of(
                new MgiSubmeshRange(0, 3, 0),
                new MgiSubmeshRange(3, 3, 1)
            )
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        byte[] bytes = codec.write(input);
        MgiStaticMesh output = codec.read(bytes);

        assertArrayEquals(input.positions(), output.positions());
        assertNull(output.normalsOrNull());
        assertNull(output.uv0OrNull());
        assertNull(output.boundsOrNull());
        assertNull(output.canonicalMetadataOrNull());
        assertArrayEquals(input.indices(), output.indices());
        assertEquals(input.submeshes(), output.submeshes());
    }

    @Test
    void roundTripsNormalsAndUv0() throws Exception {
        MgiStaticMesh input = new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f
            },
            new float[] {
                0f, 0f, 1f,
                0f, 0f, 1f,
                0f, 0f, 1f
            },
            new float[] {
                0f, 0f,
                1f, 0f,
                0f, 1f
            },
            new MgiAabb(0f, 0f, -1f, 1f, 1f, 0f),
            new MgiCanonicalMetadata(3, 3, MgiCanonicalMetadata.FLAG_DEGENERATE_FREE),
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(0, 3, 0))
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        byte[] bytes = codec.write(input);
        MgiStaticMesh output = codec.read(bytes);

        assertArrayEquals(input.positions(), output.positions());
        assertArrayEquals(input.normalsOrNull(), output.normalsOrNull());
        assertArrayEquals(input.uv0OrNull(), output.uv0OrNull());
        assertEquals(input.boundsOrNull(), output.boundsOrNull());
        assertEquals(input.canonicalMetadataOrNull(), output.canonicalMetadataOrNull());
        assertArrayEquals(input.indices(), output.indices());
    }

    @Test
    void rejectsOutOfRangeIndexOnWrite() {
        MgiStaticMesh invalid = new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f
            },
            null,
            null,
            null,
            null,
            new int[] {0, 1, 3},
            List.of(new MgiSubmeshRange(0, 3, 0))
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        assertThrows(MgiValidationException.class, () -> codec.write(invalid));
    }

    @Test
    void rejectsSubmeshRangeBeyondIndexBufferOnWrite() {
        MgiStaticMesh invalid = new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f
            },
            null,
            null,
            null,
            null,
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(2, 3, 0))
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        assertThrows(MgiValidationException.class, () -> codec.write(invalid));
    }

    @Test
    void rejectsMalformedBoundsChunkOnRead() throws Exception {
        MgiStaticMesh input = new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f
            },
            null,
            null,
            new MgiAabb(0f, 0f, 0f, 1f, 1f, 0f),
            null,
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(0, 3, 0))
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        byte[] bytes = codec.write(input);

        int boundsIndex = 5;
        int lengthFieldOffset = MgiConstants.HEADER_SIZE_BYTES
            + (boundsIndex * MgiConstants.CHUNK_ENTRY_SIZE_BYTES)
            + 16;
        bytes[lengthFieldOffset] = 8;
        bytes[lengthFieldOffset + 1] = 0;
        bytes[lengthFieldOffset + 2] = 0;
        bytes[lengthFieldOffset + 3] = 0;
        bytes[lengthFieldOffset + 4] = 0;
        bytes[lengthFieldOffset + 5] = 0;
        bytes[lengthFieldOffset + 6] = 0;
        bytes[lengthFieldOffset + 7] = 0;

        assertThrows(MgiValidationException.class, () -> codec.read(bytes));
    }

    @Test
    void rejectsMetadataCountMismatchOnWrite() {
        MgiStaticMesh invalid = new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f
            },
            null,
            null,
            null,
            new MgiCanonicalMetadata(99, 3, MgiCanonicalMetadata.FLAG_DEGENERATE_FREE),
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(0, 3, 0))
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        assertThrows(MgiValidationException.class, () -> codec.write(invalid));
    }
}
