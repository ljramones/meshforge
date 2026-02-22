package org.meshforge.test;

import org.junit.jupiter.api.Test;
import org.meshforge.pack.simd.SimdNormalPacker;
import org.vectrix.gpu.OctaNormal;
import org.vectrix.gpu.PackedNorm;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimdNormalPackerTest {
    @Test
    void simdOctaPackingMatchesScalarReference() {
        int vertexCount = 257; // non-multiple of typical SIMD lane sizes to exercise scalar tail
        float[] normals = new float[vertexCount * 3];
        Random rnd = new Random(42L);
        for (int i = 0; i < vertexCount; i++) {
            int off = i * 3;
            float x = (rnd.nextFloat() * 2.0f) - 1.0f;
            float y = (rnd.nextFloat() * 2.0f) - 1.0f;
            float z = (rnd.nextFloat() * 2.0f) - 1.0f;
            normals[off] = x;
            normals[off + 1] = y;
            normals[off + 2] = z;
        }

        int[] simd = new int[vertexCount];
        SimdNormalPacker.packOctaNormals(normals, vertexCount, simd);

        for (int i = 0; i < vertexCount; i++) {
            int off = i * 3;
            int expected = OctaNormal.encodeSnorm16(normals[off], normals[off + 1], normals[off + 2]);
            assertEquals(expected, simd[i], "packed mismatch at vertex " + i);
        }
    }

    @Test
    void simdSnorm8PackingMatchesScalarReference() {
        int vertexCount = 513; // non-multiple of typical SIMD lane sizes to exercise scalar tail
        float[] normals = new float[vertexCount * 3];
        Random rnd = new Random(7L);
        for (int i = 0; i < vertexCount; i++) {
            int off = i * 3;
            normals[off] = (rnd.nextFloat() * 4.0f) - 2.0f; // include out-of-range values for clamp behavior
            normals[off + 1] = (rnd.nextFloat() * 4.0f) - 2.0f;
            normals[off + 2] = (rnd.nextFloat() * 4.0f) - 2.0f;
        }

        int[] simd = new int[vertexCount];
        SimdNormalPacker.packSnorm8Normals(normals, vertexCount, simd);

        for (int i = 0; i < vertexCount; i++) {
            int off = i * 3;
            int expected = PackedNorm.packSnorm8x4(normals[off], normals[off + 1], normals[off + 2], 0.0f);
            assertEquals(expected, simd[i], "packed mismatch at vertex " + i);
        }
    }
}
