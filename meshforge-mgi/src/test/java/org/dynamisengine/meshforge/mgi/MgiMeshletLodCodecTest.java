package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MgiMeshletLodCodecTest {

    @Test
    void roundTripsOptionalMeshletLodChunk() throws Exception {
        MgiStaticMesh input = meshWithMeshletsAndLod();

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        byte[] bytes = codec.write(input);
        MgiStaticMesh output = codec.read(bytes);

        assertEquals(input.meshletLodDataOrNull().levels(), output.meshletLodDataOrNull().levels());
        assertEquals(input.meshletDataOrNull().descriptors(), output.meshletDataOrNull().descriptors());
    }

    @Test
    void rejectsLodMetadataWithoutMeshletDescriptors() {
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
            null,
            new MgiMeshletLodData(List.of(new MgiMeshletLodLevel(0, 0, 1, 0f))),
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(0, 3, 0))
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        assertThrows(MgiValidationException.class, () -> codec.write(invalid));
    }

    @Test
    void rejectsMalformedMeshletLodChunkOnRead() throws Exception {
        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        byte[] bytes = codec.write(meshWithMeshletsAndLod());

        int lodIndex = 9;
        int lodLengthOffset = MgiConstants.HEADER_SIZE_BYTES
            + (lodIndex * MgiConstants.CHUNK_ENTRY_SIZE_BYTES)
            + 16;
        ByteBuffer patch = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        patch.putLong(lodLengthOffset, 8L);

        assertThrows(MgiValidationException.class, () -> codec.read(bytes));
    }

    private static MgiStaticMesh meshWithMeshletsAndLod() {
        return new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f,
                1f, 1f, 0f,
                1f, 0f, 1f
            },
            null,
            null,
            null,
            null,
            new MgiMeshletData(
                List.of(
                    new MgiMeshletDescriptor(0, 0, 0, 4, 0, 2, 0, 0),
                    new MgiMeshletDescriptor(0, 0, 4, 4, 2, 2, 1, 0),
                    new MgiMeshletDescriptor(0, 0, 8, 4, 4, 2, 2, 0)
                ),
                new int[] {0, 1, 2, 3, 1, 2, 4, 5, 0, 2, 3, 5},
                new int[] {0, 1, 2, 0, 2, 3, 0, 1, 2, 1, 2, 3, 0, 1, 3, 0, 3, 2},
                List.of(
                    new MgiMeshletBounds(0f, 0f, 0f, 1f, 1f, 1f),
                    new MgiMeshletBounds(0f, 0f, 0f, 1f, 1f, 1f),
                    new MgiMeshletBounds(0f, 0f, 0f, 1f, 1f, 1f)
                )
            ),
            new MgiMeshletLodData(List.of(
                new MgiMeshletLodLevel(0, 0, 2, 0.0f),
                new MgiMeshletLodLevel(1, 2, 1, 0.8f)
            )),
            new int[] {0, 1, 2, 0, 2, 3, 1, 4, 5, 1, 5, 2, 0, 2, 3, 0, 3, 5},
            List.of(new MgiSubmeshRange(0, 18, 0))
        );
    }
}
