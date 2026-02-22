package org.meshforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshLoadersTest {
    @Test
    void rejectsUnsupportedExtension() {
        MeshLoaders loaders = MeshLoaders.defaults();
        assertThrows(IOException.class, () -> loaders.load(Path.of("model.unsupported")));
    }

    @Test
    void allowsCustomRegistration() throws Exception {
        MeshLoaders loaders = MeshLoaders.builder()
            .register("foo", path -> null)
            .build();

        assertEquals(null, loaders.load(Path.of("mesh.foo")));
    }

    @Test
    void plannedRegistryHasExplicitPlaceholderForKnownButUnimplementedFormat() {
        MeshLoaders loaders = MeshLoaders.planned();
        IOException ex = assertThrows(IOException.class, () -> loaders.load(Path.of("mesh.usd")));
        assertTrue(ex.getMessage().contains("not implemented yet"));
    }

    @Test
    void gltfMeshoptDetectionHonorsDecodeOption(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("mesh.gltf");
        Files.writeString(file, """
            {
              "asset": {"version":"2.0"},
              "buffers": [],
              "bufferViews": [{
                "byteLength": 12,
                "extensions": {
                  "KHR_meshopt_compression": {
                    "buffer": 0,
                    "byteOffset": 0,
                    "byteLength": 12,
                    "byteStride": 12,
                    "count": 1,
                    "mode": "ATTRIBUTES",
                    "filter": "NONE"
                  }
                }
              }],
              "accessors": [{"bufferView": 0, "componentType": 5126, "count": 1, "type": "VEC3"}],
              "meshes": [{"primitives": [{"attributes": {"POSITION": 0}}]}]
            }
            """, StandardCharsets.UTF_8);

        MeshLoaders loaders = MeshLoaders.planned();
        MeshLoadOptions disabled = MeshLoadOptions.builder().meshoptDecodeEnabled(false).build();
        IOException disabledEx = assertThrows(IOException.class, () -> loaders.load(file, disabled));
        assertTrue(disabledEx.getMessage().contains("meshopt decode is disabled"));

        MeshLoadOptions enabled = MeshLoadOptions.builder().meshoptDecodeEnabled(true).build();
        IOException enabledEx = assertThrows(IOException.class, () -> loaders.load(file, enabled));
        assertTrue(enabledEx.getMessage().contains("buffer index out of range"));
    }

    @Test
    void gltfWithoutMeshoptReturnsNotImplementedPlaceholder(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("plain.gltf");
        Files.writeString(file, """
            {
              "asset": {"version":"2.0"}
            }
            """, StandardCharsets.UTF_8);

        MeshLoaders loaders = MeshLoaders.planned();
        IOException ex = assertThrows(IOException.class, () -> loaders.load(file, MeshLoadOptions.defaults()));
        assertTrue(ex.getMessage().contains("buffers"));
    }

    @Test
    void defaultsStillRejectUnknownFormats() {
        MeshLoaders loaders = MeshLoaders.defaults();
        IOException ex = assertThrows(IOException.class, () -> loaders.load(Path.of("mesh.xyz")));
        assertTrue(ex.getMessage().contains("Unsupported mesh format"));
    }

    @Test
    void defaultsDispatchesStlLoader(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("simple.stl");
        Files.write(file, binaryTriangleStl());

        var mesh = MeshLoaders.defaults().load(file);
        assertEquals(3, mesh.vertexCount());
        assertEquals(3, mesh.indicesOrNull().length);
    }

    @Test
    void defaultsFastDispatchesObjFastLoader(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("simple.obj");
        Files.writeString(file, """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """, StandardCharsets.US_ASCII);

        var mesh = MeshLoaders.defaultsFast().load(file);
        assertEquals(3, mesh.vertexCount());
        assertEquals(3, mesh.indicesOrNull().length);
    }

    private static byte[] binaryTriangleStl() {
        byte[] bytes = new byte[84 + 50];
        byte[] header = "meshforge-binary-stl".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(header, 0, bytes, 0, header.length);

        ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(1);
        ByteBuffer tri = ByteBuffer.wrap(bytes, 84, 50).order(ByteOrder.LITTLE_ENDIAN);
        tri.putFloat(0f).putFloat(0f).putFloat(1f);
        tri.putFloat(0f).putFloat(0f).putFloat(0f);
        tri.putFloat(1f).putFloat(0f).putFloat(0f);
        tri.putFloat(0f).putFloat(1f).putFloat(0f);
        tri.putShort((short) 0);

        return bytes;
    }
}
