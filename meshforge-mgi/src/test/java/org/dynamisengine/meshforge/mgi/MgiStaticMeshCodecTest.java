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
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(0, 3, 0))
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        byte[] bytes = codec.write(input);
        MgiStaticMesh output = codec.read(bytes);

        assertArrayEquals(input.positions(), output.positions());
        assertArrayEquals(input.normalsOrNull(), output.normalsOrNull());
        assertArrayEquals(input.uv0OrNull(), output.uv0OrNull());
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
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(2, 3, 0))
        );

        MgiStaticMeshCodec codec = new MgiStaticMeshCodec();
        assertThrows(MgiValidationException.class, () -> codec.write(invalid));
    }
}
