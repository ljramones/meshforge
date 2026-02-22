package org.meshforge.loader;

import org.meshforge.core.mesh.MeshData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GltfMeshLoaderTest {
    @Test
    void loadsMeshoptCompressedGltfWhenDecodeEnabled(@TempDir Path tempDir) throws Exception {
        byte[] rawPositions = floatVec3Bytes(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        );
        byte[] rawIndices = u32Bytes(0, 1, 2);
        byte[] compressedPositions = lz4Literal(rawPositions);
        byte[] compressedIndices = lz4Literal(rawIndices);

        byte[] payload = concat(compressedPositions, compressedIndices);
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(payload);

        int posCompressedOffset = 0;
        int idxCompressedOffset = compressedPositions.length;

        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [
                {"byteLength": %d, "uri": "%s"}
              ],
              "bufferViews": [
                {
                  "byteLength": %d,
                  "extensions": {
                    "KHR_meshopt_compression": {
                      "buffer": 0,
                      "byteOffset": %d,
                      "byteLength": %d,
                      "byteStride": 12,
                      "count": 3,
                      "mode": "ATTRIBUTES",
                      "filter": "NONE"
                    }
                  }
                },
                {
                  "byteLength": %d,
                  "extensions": {
                    "KHR_meshopt_compression": {
                      "buffer": 0,
                      "byteOffset": %d,
                      "byteLength": %d,
                      "byteStride": 4,
                      "count": 3,
                      "mode": "TRIANGLES",
                      "filter": "NONE"
                    }
                  }
                }
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3"},
                {"bufferView": 1, "componentType": 5125, "count": 3, "type": "SCALAR"}
              ],
              "meshes": [
                {
                  "primitives": [
                    {"attributes": {"POSITION": 0}, "indices": 1}
                  ]
                }
              ]
            }
            """.formatted(
            payload.length,
            uri,
            rawPositions.length,
            posCompressedOffset,
            compressedPositions.length,
            rawIndices.length,
            idxCompressedOffset,
            compressedIndices.length
        );

        Path file = tempDir.resolve("meshopt.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        MeshData mesh = MeshLoaders.planned().load(file, MeshLoadOptions.defaults());
        assertEquals(3, mesh.vertexCount());
        assertEquals(3, mesh.indicesOrNull().length);
        assertEquals(1f, mesh.attribute(org.meshforge.core.attr.AttributeSemantic.POSITION, 0).getFloat(1, 0));
        assertEquals(1f, mesh.attribute(org.meshforge.core.attr.AttributeSemantic.POSITION, 0).getFloat(2, 1));
        assertEquals(2, mesh.indicesOrNull()[2]);
    }

    @Test
    void rejectsMeshoptCompressedGltfWhenDecodeDisabled(@TempDir Path tempDir) throws Exception {
        byte[] compressed = lz4Literal(new byte[12]);
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(compressed);
        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d, "uri": "%s"}],
              "bufferViews": [{
                "byteLength": 12,
                "extensions": {
                  "KHR_meshopt_compression": {
                    "buffer": 0,
                    "byteOffset": 0,
                    "byteLength": %d,
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
            """.formatted(compressed.length, uri, compressed.length);
        Path file = tempDir.resolve("meshopt-disabled.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        MeshLoadOptions disabled = MeshLoadOptions.builder().meshoptDecodeEnabled(false).build();
        IOException ex = assertThrows(IOException.class, () -> MeshLoaders.planned().load(file, disabled));
        assertTrue(ex.getMessage().contains("meshopt decode is disabled"));
    }

    private static byte[] lz4Literal(byte[] raw) {
        int len = raw.length;
        if (len < 15) {
            byte[] out = new byte[1 + len];
            out[0] = (byte) (len << 4);
            System.arraycopy(raw, 0, out, 1, len);
            return out;
        }
        int ext = len - 15;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write((byte) 0xF0);
        while (ext >= 255) {
            out.write((byte) 255);
            ext -= 255;
        }
        out.write((byte) ext);
        out.write(raw, 0, raw.length);
        return out.toByteArray();
    }

    private static byte[] floatVec3Bytes(float... values) {
        ByteBuffer bb = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            bb.putFloat(value);
        }
        return bb.array();
    }

    private static byte[] u32Bytes(int... values) {
        ByteBuffer bb = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) {
            bb.putInt(value);
        }
        return bb.array();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
