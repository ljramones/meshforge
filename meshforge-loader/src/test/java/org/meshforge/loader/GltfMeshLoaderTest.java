package org.meshforge.loader;

import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.mesh.MorphTarget;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GltfMeshLoaderTest {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION_2 = 2;
    private static final int GLB_CHUNK_JSON = 0x4E4F534A;
    private static final int GLB_CHUNK_BIN = 0x004E4942;

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

    @Test
    void loadsMeshoptCompressedGlbWhenDecodeEnabled(@TempDir Path tempDir) throws Exception {
        byte[] rawPositions = floatVec3Bytes(
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        );
        byte[] rawIndices = u32Bytes(0, 1, 2);
        byte[] compressedPositions = lz4Literal(rawPositions);
        byte[] compressedIndices = lz4Literal(rawIndices);
        byte[] binPayload = concat(compressedPositions, compressedIndices);

        String json = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d}],
              "bufferViews": [
                {
                  "byteLength": %d,
                  "extensions": {
                    "KHR_meshopt_compression": {
                      "buffer": 0,
                      "byteOffset": 0,
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
              "meshes": [{"primitives": [{"attributes": {"POSITION": 0}, "indices": 1}]}]
            }
            """.formatted(
            binPayload.length,
            rawPositions.length,
            compressedPositions.length,
            rawIndices.length,
            compressedPositions.length,
            compressedIndices.length
        );

        byte[] glb = buildGlb(json, binPayload);
        Path file = tempDir.resolve("meshopt.glb");
        Files.write(file, glb);

        MeshData mesh = MeshLoaders.planned().load(file, MeshLoadOptions.defaults());
        assertEquals(3, mesh.vertexCount());
        assertEquals(3, mesh.indicesOrNull().length);
        assertEquals(1f, mesh.attribute(org.meshforge.core.attr.AttributeSemantic.POSITION, 0).getFloat(1, 0));
        assertEquals(1f, mesh.attribute(org.meshforge.core.attr.AttributeSemantic.POSITION, 0).getFloat(2, 1));
        assertEquals(2, mesh.indicesOrNull()[2]);
    }

    @Test
    void loadsSkinnedAttributesAndNormalizesWeights(@TempDir Path tempDir) throws Exception {
        byte[] positions = floatVec3Bytes(0f, 0f, 0f, 1f, 0f, 0f);
        byte[] joints = u8Vec4Bytes(
            0, 1, 2, 3,
            3, 2, 1, 0
        );
        byte[] weights = floatVec4Bytes(
            2f, 1f, 1f, 0f,
            0f, 0f, 0f, 0f
        );
        byte[] bin = concat(positions, concat(joints, weights));
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin);
        int jointsOffset = positions.length;
        int weightsOffset = positions.length + joints.length;

        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d, "uri": "%s"}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 2, "type": "VEC3"},
                {"bufferView": 1, "componentType": 5121, "count": 2, "type": "VEC4"},
                {"bufferView": 2, "componentType": 5126, "count": 2, "type": "VEC4"}
              ],
              "skins": [{"joints": [0,1,2,3]}],
              "meshes": [{
                "primitives": [{
                  "attributes": {"POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2},
                  "skin": 0
                }]
              }]
            }
            """.formatted(bin.length, uri, positions.length, jointsOffset, joints.length, weightsOffset, weights.length);

        Path file = tempDir.resolve("skinned.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        MeshData mesh = MeshLoaders.planned().load(file, MeshLoadOptions.defaults());
        assertTrue(mesh.has(AttributeSemantic.JOINTS, 0));
        assertTrue(mesh.has(AttributeSemantic.WEIGHTS, 0));

        var jointsView = mesh.attribute(AttributeSemantic.JOINTS, 0);
        assertEquals(3, jointsView.getInt(0, 3));
        assertEquals(3, jointsView.getInt(1, 0));

        var weightsView = mesh.attribute(AttributeSemantic.WEIGHTS, 0);
        float w0 = weightsView.getFloat(0, 0);
        float w1 = weightsView.getFloat(0, 1);
        float w2 = weightsView.getFloat(0, 2);
        float w3 = weightsView.getFloat(0, 3);
        assertEquals(1.0f, w0 + w1 + w2 + w3, 1.0e-6f);
        assertEquals(0.5f, w0, 1.0e-6f);
        assertEquals(0.25f, w1, 1.0e-6f);
        assertEquals(0.25f, w2, 1.0e-6f);
        assertEquals(0.0f, w3, 1.0e-6f);
        assertEquals(1.0f, weightsView.getFloat(1, 0), 1.0e-6f);
    }

    @Test
    void rejectsJointIndicesOutsideSkinJointCount(@TempDir Path tempDir) throws Exception {
        byte[] positions = floatVec3Bytes(0f, 0f, 0f);
        byte[] joints = u8Vec4Bytes(0, 1, 2, 4);
        byte[] weights = floatVec4Bytes(1f, 0f, 0f, 0f);
        byte[] bin = concat(positions, concat(joints, weights));
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin);
        int jointsOffset = positions.length;
        int weightsOffset = positions.length + joints.length;

        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d, "uri": "%s"}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 1, "type": "VEC3"},
                {"bufferView": 1, "componentType": 5121, "count": 1, "type": "VEC4"},
                {"bufferView": 2, "componentType": 5126, "count": 1, "type": "VEC4"}
              ],
              "skins": [{"joints": [0,1,2,3]}],
              "meshes": [{
                "primitives": [{
                  "attributes": {"POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2},
                  "skin": 0
                }]
              }]
            }
            """.formatted(bin.length, uri, positions.length, jointsOffset, joints.length, weightsOffset, weights.length);

        Path file = tempDir.resolve("skinned-invalid.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        IOException ex = assertThrows(IOException.class, () -> MeshLoaders.planned().load(file, MeshLoadOptions.defaults()));
        assertTrue(ex.getMessage().contains("JOINTS_0 value out of range"));
    }

    @Test
    void validatesAgainstReferencedSkinNotFirstSkin(@TempDir Path tempDir) throws Exception {
        byte[] positions = floatVec3Bytes(0f, 0f, 0f);
        byte[] joints = u8Vec4Bytes(3, 0, 0, 0); // valid for skin 0 (4 joints), invalid for skin 1 (2 joints)
        byte[] weights = floatVec4Bytes(1f, 0f, 0f, 0f);
        byte[] bin = concat(positions, concat(joints, weights));
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin);
        int jointsOffset = positions.length;
        int weightsOffset = positions.length + joints.length;

        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d, "uri": "%s"}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 1, "type": "VEC3"},
                {"bufferView": 1, "componentType": 5121, "count": 1, "type": "VEC4"},
                {"bufferView": 2, "componentType": 5126, "count": 1, "type": "VEC4"}
              ],
              "skins": [
                {"joints": [0,1,2,3]},
                {"joints": [0,1]}
              ],
              "meshes": [{
                "primitives": [{
                  "attributes": {"POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2},
                  "skin": 1
                }]
              }]
            }
            """.formatted(bin.length, uri, positions.length, jointsOffset, joints.length, weightsOffset, weights.length);

        Path file = tempDir.resolve("skin-ref-1.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        IOException ex = assertThrows(IOException.class, () -> MeshLoaders.planned().load(file, MeshLoadOptions.defaults()));
        assertTrue(ex.getMessage().contains("JOINTS_0 value out of range"));
    }

    @Test
    void rejectsJointsWhenNoSkinReferenceIsResolvable(@TempDir Path tempDir) throws Exception {
        byte[] positions = floatVec3Bytes(0f, 0f, 0f);
        byte[] joints = u8Vec4Bytes(0, 0, 0, 0);
        byte[] weights = floatVec4Bytes(1f, 0f, 0f, 0f);
        byte[] bin = concat(positions, concat(joints, weights));
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin);
        int jointsOffset = positions.length;
        int weightsOffset = positions.length + joints.length;

        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d, "uri": "%s"}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 1, "type": "VEC3"},
                {"bufferView": 1, "componentType": 5121, "count": 1, "type": "VEC4"},
                {"bufferView": 2, "componentType": 5126, "count": 1, "type": "VEC4"}
              ],
              "skins": [{"joints": [0,1,2,3]}],
              "meshes": [{
                "primitives": [{
                  "attributes": {"POSITION": 0, "JOINTS_0": 1, "WEIGHTS_0": 2}
                }]
              }]
            }
            """.formatted(bin.length, uri, positions.length, jointsOffset, joints.length, weightsOffset, weights.length);

        Path file = tempDir.resolve("no-skin-ref.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        IOException ex = assertThrows(IOException.class, () -> MeshLoaders.planned().load(file, MeshLoadOptions.defaults()));
        assertTrue(ex.getMessage().contains("no skin reference could be resolved"));
    }

    @Test
    void loadsTwoMorphTargetsWithNamesAndOptionalChannels(@TempDir Path tempDir) throws Exception {
        byte[] positions = floatVec3Bytes(0f, 0f, 0f, 1f, 0f, 0f);
        byte[] t0Pos = floatVec3Bytes(0.1f, 0f, 0f, 0f, 0.1f, 0f);
        byte[] t0Nrm = floatVec3Bytes(0f, 0f, 1f, 0f, 0f, 1f);
        byte[] t1Pos = floatVec3Bytes(-0.1f, 0f, 0f, 0f, -0.1f, 0f);
        byte[] bin = concat(positions, concat(t0Pos, concat(t0Nrm, t1Pos)));
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin);

        int off0 = 0;
        int off1 = positions.length;
        int off2 = positions.length + t0Pos.length;
        int off3 = positions.length + t0Pos.length + t0Nrm.length;

        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d, "uri": "%s"}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": %d, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 2, "type": "VEC3"},
                {"bufferView": 1, "componentType": 5126, "count": 2, "type": "VEC3"},
                {"bufferView": 2, "componentType": 5126, "count": 2, "type": "VEC3"},
                {"bufferView": 3, "componentType": 5126, "count": 2, "type": "VEC3"}
              ],
              "meshes": [{
                "extras": {"targetNames": ["Smile", "Blink"]},
                "primitives": [{
                  "attributes": {"POSITION": 0},
                  "targets": [
                    {"POSITION": 1, "NORMAL": 2},
                    {"POSITION": 3}
                  ]
                }]
              }]
            }
            """.formatted(
            bin.length, uri,
            off0, positions.length,
            off1, t0Pos.length,
            off2, t0Nrm.length,
            off3, t1Pos.length
        );

        Path file = tempDir.resolve("morphs.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        MeshData mesh = MeshLoaders.planned().load(file, MeshLoadOptions.defaults());
        assertEquals(2, mesh.morphTargets().size());

        MorphTarget smile = mesh.morphTargets().get(0);
        MorphTarget blink = mesh.morphTargets().get(1);
        assertEquals("Smile", smile.name());
        assertEquals("Blink", blink.name());
        assertEquals(6, smile.positionDeltas().length);
        assertNotNull(smile.normalDeltas());
        assertNull(smile.tangentDeltas());
        assertEquals(6, blink.positionDeltas().length);
        assertNull(blink.normalDeltas());
        assertNull(blink.tangentDeltas());
    }

    @Test
    void meshWithNoMorphTargetsLoadsUnchanged(@TempDir Path tempDir) throws Exception {
        byte[] positions = floatVec3Bytes(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(positions);
        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d, "uri": "%s"}],
              "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": %d}],
              "accessors": [{"bufferView": 0, "componentType": 5126, "count": 3, "type": "VEC3"}],
              "meshes": [{"primitives": [{"attributes": {"POSITION": 0}}]}]
            }
            """.formatted(positions.length, uri, positions.length);
        Path file = tempDir.resolve("no-morphs.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        MeshData mesh = MeshLoaders.planned().load(file, MeshLoadOptions.defaults());
        assertTrue(mesh.morphTargets().isEmpty());
    }

    @Test
    void rejectsMorphTargetWithInvalidAccessorType(@TempDir Path tempDir) throws Exception {
        byte[] positions = floatVec3Bytes(0f, 0f, 0f);
        byte[] invalidDelta = u32Bytes(1); // SCALAR/UINT instead of VEC3/FLOAT
        byte[] bin = concat(positions, invalidDelta);
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(bin);
        int deltaOffset = positions.length;
        String gltf = """
            {
              "asset": {"version":"2.0"},
              "buffers": [{"byteLength": %d, "uri": "%s"}],
              "bufferViews": [
                {"buffer": 0, "byteOffset": 0, "byteLength": %d},
                {"buffer": 0, "byteOffset": %d, "byteLength": %d}
              ],
              "accessors": [
                {"bufferView": 0, "componentType": 5126, "count": 1, "type": "VEC3"},
                {"bufferView": 1, "componentType": 5125, "count": 1, "type": "SCALAR"}
              ],
              "meshes": [{
                "primitives": [{
                  "attributes": {"POSITION": 0},
                  "targets": [{"POSITION": 1}]
                }]
              }]
            }
            """.formatted(bin.length, uri, positions.length, deltaOffset, invalidDelta.length);
        Path file = tempDir.resolve("bad-morph.gltf");
        Files.writeString(file, gltf, StandardCharsets.UTF_8);

        IOException ex = assertThrows(IOException.class, () -> MeshLoaders.planned().load(file, MeshLoadOptions.defaults()));
        assertTrue(ex.getMessage().contains("must be VEC3/FLOAT"));
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

    private static byte[] floatVec4Bytes(float... values) {
        ByteBuffer bb = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : values) {
            bb.putFloat(value);
        }
        return bb.array();
    }

    private static byte[] u8Vec4Bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) (values[i] & 0xFF);
        }
        return out;
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

    private static byte[] buildGlb(String json, byte[] binPayload) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        int jsonPadded = align4(jsonBytes.length);
        int binPadded = align4(binPayload.length);
        int totalLength = 12 + 8 + jsonPadded + 8 + binPadded;

        ByteBuffer bb = ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt(GLB_MAGIC);
        bb.putInt(GLB_VERSION_2);
        bb.putInt(totalLength);

        bb.putInt(jsonPadded);
        bb.putInt(GLB_CHUNK_JSON);
        bb.put(jsonBytes);
        while ((bb.position() & 3) != 0) {
            bb.put((byte) 0x20);
        }

        bb.putInt(binPadded);
        bb.putInt(GLB_CHUNK_BIN);
        bb.put(binPayload);
        while ((bb.position() & 3) != 0) {
            bb.put((byte) 0);
        }
        return bb.array();
    }

    private static int align4(int n) {
        return (n + 3) & ~3;
    }
}
