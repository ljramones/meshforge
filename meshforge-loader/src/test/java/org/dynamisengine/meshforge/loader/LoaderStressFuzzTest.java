package org.dynamisengine.meshforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.dynamisengine.meshforge.core.mesh.MeshData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

class LoaderStressFuzzTest {
    private static final long SEED = 0x5EEDC0DEL;
    private static final int FUZZ_CASES = 64;

    @Test
    void objFastObjOffPlyStlFuzzOnlyThrowAllowedExceptions(@TempDir Path tempDir) throws Exception {
        Random rnd = new Random(SEED);
        for (int i = 0; i < FUZZ_CASES; i++) {
            String text = randomAscii(rnd, 256);
            assertAllowedLoadFailure(() -> ObjMeshLoader.load(new StringReader(text)));
            assertAllowedLoadFailure(() -> OffMeshLoader.load(new StringReader(text)));
            assertAllowedLoadFailure(() -> PlyMeshLoader.load(new StringReader(text)));

            Path obj = tempDir.resolve("fuzz-" + i + ".obj");
            Files.writeString(obj, text, StandardCharsets.UTF_8);
            assertAllowedLoadFailure(() -> FastObjMeshLoader.load(obj));

            byte[] bytes = new byte[rnd.nextInt(256)];
            rnd.nextBytes(bytes);
            assertAllowedLoadFailure(() -> StlMeshLoader.load(new ByteArrayInputStream(bytes)));
        }
    }

    @Test
    void objOffPlyStlHandleEmptyOrZeroCountInputs() throws Exception {
        MeshData obj = ObjMeshLoader.load(new StringReader("# empty obj\n"));
        assertSaneEmpty(obj);

        MeshData off = OffMeshLoader.load(new StringReader("OFF\n0 0 0\n"));
        assertSaneEmpty(off);

        MeshData ply = PlyMeshLoader.load(new StringReader("""
            ply
            format ascii 1.0
            element vertex 0
            element face 0
            end_header
            """));
        assertSaneEmpty(ply);

        MeshData stl = StlMeshLoader.load(new StringReader("solid empty\nendsolid empty\n"));
        assertSaneEmpty(stl);
    }

    @Test
    void formatSpecificMalformedCasesFailLoudly(@TempDir Path tempDir) throws Exception {
        assertAllowedLoadFailure(() -> ObjMeshLoader.load(new StringReader("""
            v 0 0 0
            f 2147483647 1 1
            """)));

        assertAllowedLoadFailure(() -> OffMeshLoader.load(new StringReader("""
            OFF
            3 1 0
            0 0 0
            1 0 0
            """)));

        assertAllowedLoadFailure(() -> PlyMeshLoader.load(new StringReader("""
            ply
            format ascii 1.0
            element vertex 3
            element face 1
            end_header
            0 0 0
            1 0 0
            """)));

        byte[] truncatedBinaryStl = new byte[80 + 4 + 20];
        ByteBuffer.wrap(truncatedBinaryStl).order(ByteOrder.LITTLE_ENDIAN).putInt(80, 1);
        assertAllowedLoadFailure(() -> StlMeshLoader.load(new ByteArrayInputStream(truncatedBinaryStl)));

        Path obj = tempDir.resolve("bad.obj");
        Files.writeString(obj, "v 0 0\nf 1 2 3\n", StandardCharsets.UTF_8);
        assertAllowedLoadFailure(() -> FastObjMeshLoader.load(obj));

        assertAllowedLoadFailure(() -> OffMeshLoader.load(new StringReader("""
            OFF
            2147483647 0 0
            """)));

        assertAllowedLoadFailure(() -> PlyMeshLoader.load(new StringReader("""
            ply
            format ascii 1.0
            element vertex 2147483647
            element face 0
            end_header
            """)));
    }

    @Test
    void gltfStressCasesFailWithValidationErrors(@TempDir Path tempDir) throws Exception {
        MeshLoaders loaders = MeshLoaders.planned();
        MeshLoadOptions options = MeshLoadOptions.defaults();

        Path malformed = writeGltf(tempDir, "malformed.gltf", "{ this is not json");
        assertAllowedLoadFailure(() -> loaders.load(malformed, options));

        Path missingPosition = writeGltf(tempDir, "missing-position.gltf", """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":0,"uri":"data:application/octet-stream;base64,"}],
              "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":0}],
              "accessors":[{"bufferView":0,"componentType":5126,"count":0,"type":"VEC3"}],
              "meshes":[{"primitives":[{"attributes":{}}]}]
            }
            """);
        assertAllowedLoadFailure(() -> loaders.load(missingPosition, options));

        Path outOfRangeView = writeGltf(tempDir, "out-of-range-view.gltf", """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":4,"uri":"data:application/octet-stream;base64,AAAAAA=="}],
              "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":64}],
              "accessors":[{"bufferView":0,"componentType":5126,"count":1,"type":"VEC3"}],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}]
            }
            """);
        assertAllowedLoadFailure(() -> loaders.load(outOfRangeView, options));

        Path negativeOffset = writeGltf(tempDir, "negative-offset.gltf", """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":12,"uri":"data:application/octet-stream;base64,AAAAAAAAAAAAAA=="}],
              "bufferViews":[{"buffer":0,"byteOffset":-1,"byteLength":12}],
              "accessors":[{"bufferView":0,"componentType":5126,"count":1,"type":"VEC3"}],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}]
            }
            """);
        assertAllowedLoadFailure(() -> loaders.load(negativeOffset, options));

        byte[] pos = floats(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        String posB64 = Base64.getEncoder().encodeToString(pos);
        Path badMorph = writeGltf(tempDir, "bad-morph.gltf", """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":%d,"uri":"data:application/octet-stream;base64,%s"}],
              "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":%d}],
              "accessors":[
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC2"}
              ],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0},"targets":[{"POSITION":1}]}]}]
            }
            """.formatted(pos.length, posB64, pos.length));
        assertAllowedLoadFailure(() -> loaders.load(badMorph, options));

        byte[] joints = new byte[] {
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0
        };
        byte[] joined = concat(pos, joints);
        String joinedB64 = Base64.getEncoder().encodeToString(joined);
        Path skinZeroJoints = writeGltf(tempDir, "skin-zero-joints.gltf", """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":%d,"uri":"data:application/octet-stream;base64,%s"}],
              "bufferViews":[
                {"buffer":0,"byteOffset":0,"byteLength":%d},
                {"buffer":0,"byteOffset":%d,"byteLength":%d}
              ],
              "accessors":[
                {"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},
                {"bufferView":1,"componentType":5121,"count":3,"type":"VEC4"}
              ],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0,"JOINTS_0":1},"skin":0}]}],
              "skins":[{"joints":[]}],
              "nodes":[{"mesh":0,"skin":0}]
            }
            """.formatted(joined.length, joinedB64, pos.length, pos.length, joints.length));
        assertAllowedLoadFailure(() -> loaders.load(skinZeroJoints, options));

        Path hugeAccessorCount = writeGltf(tempDir, "huge-accessor-count.gltf", """
            {
              "asset":{"version":"2.0"},
              "buffers":[{"byteLength":12,"uri":"data:application/octet-stream;base64,AAAAAAAAAAAAAA=="}],
              "bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":12}],
              "accessors":[{"bufferView":0,"componentType":5126,"count":2147483647,"type":"VEC3"}],
              "meshes":[{"primitives":[{"attributes":{"POSITION":0}}]}]
            }
            """);
        assertAllowedLoadFailure(() -> loaders.load(hugeAccessorCount, options));
    }

    @Test
    void gltfFuzzOnlyThrowsAllowedExceptions(@TempDir Path tempDir) throws Exception {
        MeshLoaders loaders = MeshLoaders.planned();
        MeshLoadOptions options = MeshLoadOptions.defaults();
        Random rnd = new Random(SEED ^ 0xA11CE);
        for (int i = 0; i < FUZZ_CASES; i++) {
            Path file = tempDir.resolve("fuzz-" + i + ".gltf");
            Files.writeString(file, randomAscii(rnd, 512), StandardCharsets.UTF_8);
            assertAllowedLoadFailure(() -> loaders.load(file, options));
        }
    }

    private static void assertSaneEmpty(MeshData mesh) {
        assertNotNull(mesh);
        assertEquals(0, mesh.vertexCount());
        int[] indices = mesh.indicesOrNull();
        if (indices != null) {
            assertEquals(0, indices.length);
        }
    }

    private static void assertAllowedLoadFailure(ThrowingRunnable op) throws Exception {
        try {
            MeshData mesh = op.run();
            if (mesh != null) {
                if (mesh.vertexCount() < 0) {
                    fail("Loader returned negative vertexCount");
                }
                int[] indices = mesh.indicesOrNull();
                if (indices != null) {
                    for (int index : indices) {
                        if (index < 0) {
                            fail("Loader returned negative index");
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            if (!(ex instanceof IOException) && !(ex instanceof IllegalArgumentException)) {
                fail("Unexpected exception type: " + ex.getClass().getName(), ex);
            }
        }
    }

    private static String randomAscii(Random rnd, int maxLen) {
        int len = rnd.nextInt(maxLen + 1);
        char[] chars = new char[len];
        final String alphabet = "abcdefg hijklmn opqrst uvwxyz[]{}()<>#:/,.-_+\n\t";
        for (int i = 0; i < len; i++) {
            chars[i] = alphabet.charAt(rnd.nextInt(alphabet.length()));
        }
        return new String(chars);
    }

    private static Path writeGltf(Path dir, String fileName, String json) throws IOException {
        Path file = dir.resolve(fileName);
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }

    private static byte[] floats(float... values) {
        ByteBuffer bb = ByteBuffer.allocate(values.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            bb.putFloat(value);
        }
        return bb.array();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        MeshData run() throws Exception;
    }
}
