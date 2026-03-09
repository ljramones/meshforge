package org.dynamisengine.meshforge.mgi;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.bounds.Aabbf;
import org.dynamisengine.meshforge.core.bounds.Boundsf;
import org.dynamisengine.meshforge.core.bounds.Spheref;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MgiMeshDataCodecTest {

    @Test
    void roundTripsCanonicalMeshDataThroughMgi() throws Exception {
        MeshData input = sampleTriangleMeshWithNormalsUv();
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        MgiStaticMesh exported = MgiMeshDataCodec.toMgiStaticMesh(input);

        assertNotNull(exported.canonicalMetadataOrNull());
        assertEquals(input.vertexCount(), exported.canonicalMetadataOrNull().canonicalVertexCount());
        assertEquals(input.indicesOrNull().length, exported.canonicalMetadataOrNull().canonicalIndexCount());
        assertTrue(exported.canonicalMetadataOrNull().degenerateFree());
        assertTrue(exported.canonicalMetadataOrNull().trustedCanonical());

        byte[] bytes = codec.write(input);
        MeshData output = codec.read(bytes);

        assertEquals(Topology.TRIANGLES, output.topology());
        assertEquals(input.vertexCount(), output.vertexCount());
        assertArrayEquals(input.indicesOrNull(), output.indicesOrNull());
        assertEquals(input.submeshes(), output.submeshes());

        assertAttrEquals(input, output, AttributeSemantic.POSITION, 0, 3);
        assertAttrEquals(input, output, AttributeSemantic.NORMAL, 0, 3);
        assertAttrEquals(input, output, AttributeSemantic.UV, 0, 2);
        assertNotNull(output.boundsOrNull());
        assertNotNull(output.boundsOrNull().aabb());
        assertEquals(0f, output.boundsOrNull().aabb().minX());
        assertEquals(0f, output.boundsOrNull().aabb().minY());
        assertEquals(0f, output.boundsOrNull().aabb().minZ());
        assertEquals(1f, output.boundsOrNull().aabb().maxX());
        assertEquals(1f, output.boundsOrNull().aabb().maxY());
        assertEquals(0f, output.boundsOrNull().aabb().maxZ());
        assertNotEquals(0f, output.boundsOrNull().sphere().radius());
    }

    @Test
    void reconstructedMeshEntersRuntimePrepPath() throws Exception {
        MeshData input = sampleTriangleMeshWithNormalsUv();
        MgiMeshDataCodec codec = new MgiMeshDataCodec();

        MeshData reconstructed = codec.read(codec.write(input));
        MeshData processed = Pipelines.realtimeFast(reconstructed);
        PackedMesh packed = MeshPacker.pack(processed, Packers.realtimeFast());

        assertTrue(packed.vertexBuffer().remaining() > 0);
        assertEquals(reconstructed.indicesOrNull().length, packed.indexBuffer().indexCount());
        assertEquals(reconstructed.submeshes().size(), packed.submeshes().size());
    }

    @Test
    void rejectsNonTriangleTopology() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();
        MeshData lines = new MeshData(
            Topology.LINES,
            schema,
            2,
            new int[] {0, 1},
            List.of(new Submesh(0, 2, 0))
        );

        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        assertThrows(IllegalArgumentException.class, () -> codec.write(lines));
    }

    @Test
    void mapsNonNumericMaterialIdToStableIntegerSlots() throws Exception {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(
                new Submesh(0, 3, "matA"),
                new Submesh(0, 3, "matA"),
                new Submesh(0, 3, "matB")
            )
        );
        VertexAttributeView pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        pos.set3f(0, 0f, 0f, 0f);
        pos.set3f(1, 1f, 0f, 0f);
        pos.set3f(2, 0f, 1f, 0f);

        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        MeshData read = codec.read(codec.write(mesh));

        Object slotA0 = read.submeshes().get(0).materialId();
        Object slotA1 = read.submeshes().get(1).materialId();
        Object slotB = read.submeshes().get(2).materialId();
        assertNotNull(slotA0);
        assertNotNull(slotB);
        assertEquals(slotA0, slotA1);
        assertTrue(!slotA0.equals(slotB));
    }

    @Test
    void runtimeDecodeExposesMeshletMetadataWhenPresent() throws Exception {
        MgiStaticMesh staticMesh = new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
            },
            null,
            null,
            null,
            null,
            new MgiMeshletData(
                List.of(new MgiMeshletDescriptor(0, 0, 0, 4, 0, 2, 0, 0)),
                new int[] {0, 1, 2, 3},
                new int[] {0, 1, 2, 0, 2, 3},
                List.of(new MgiMeshletBounds(0f, 0f, 0f, 1f, 1f, 1f))
            ),
            null,
            new int[] {0, 1, 2, 0, 2, 3},
            List.of(new MgiSubmeshRange(0, 6, 0))
        );

        byte[] bytes = new MgiStaticMeshCodec().write(staticMesh);
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(bytes);

        assertTrue(decoded.meshletDataPresent());
        assertEquals(1, decoded.meshletDescriptorCount());
        assertNotNull(decoded.meshletDataOrNull());
        assertArrayEquals(new int[] {0, 1, 2, 3}, decoded.meshletDataOrNull().vertexRemap());
        assertArrayEquals(new int[] {0, 1, 2, 0, 2, 3}, decoded.meshletDataOrNull().triangles());
    }

    @Test
    void runtimeDecodeWithoutMeshletsPreservesClassicPath() throws Exception {
        MeshData input = sampleTriangleMeshWithNormalsUv();
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(codec.write(input));

        assertFalse(decoded.meshletDataPresent());
        assertEquals(0, decoded.meshletDescriptorCount());
        assertEquals(input.vertexCount(), decoded.meshData().vertexCount());
    }

    @Test
    void runtimeDecodeRejectsMalformedMeshletMetadata() throws Exception {
        MgiStaticMesh staticMesh = new MgiStaticMesh(
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                0f, 1f, 0f,
                0f, 0f, 1f
            },
            null,
            null,
            null,
            null,
            new MgiMeshletData(
                List.of(new MgiMeshletDescriptor(0, 0, 0, 4, 0, 2, 0, 0)),
                new int[] {0, 1, 2, 3},
                new int[] {0, 1, 2, 0, 2, 3},
                List.of(new MgiMeshletBounds(0f, 0f, 0f, 1f, 1f, 1f))
            ),
            null,
            new int[] {0, 1, 2, 0, 2, 3},
            List.of(new MgiSubmeshRange(0, 6, 0))
        );

        byte[] bytes = new MgiStaticMeshCodec().write(staticMesh);
        int descriptorLengthOffset = MgiConstants.HEADER_SIZE_BYTES
            + (5 * MgiConstants.CHUNK_ENTRY_SIZE_BYTES)
            + 16;
        ByteBuffer patch = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        patch.putLong(descriptorLengthOffset, (long) (7 * Integer.BYTES));

        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        assertThrows(MgiValidationException.class, () -> codec.readForRuntime(bytes));
    }

    private static void assertAttrEquals(
        MeshData input,
        MeshData output,
        AttributeSemantic semantic,
        int setIndex,
        int components
    ) {
        VertexAttributeView in = input.attribute(semantic, setIndex);
        VertexAttributeView out = output.attribute(semantic, setIndex);
        for (int i = 0; i < input.vertexCount(); i++) {
            for (int c = 0; c < components; c++) {
                assertEquals(in.getFloat(i, c), out.getFloat(i, c));
            }
        }
    }

    private static MeshData sampleTriangleMeshWithNormalsUv() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .build();
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            4,
            new int[] {0, 1, 2, 1, 3, 2},
            List.of(new Submesh(0, 3, 0), new Submesh(3, 3, 1))
        );

        VertexAttributeView pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        pos.set3f(0, 0f, 0f, 0f);
        pos.set3f(1, 1f, 0f, 0f);
        pos.set3f(2, 0f, 1f, 0f);
        pos.set3f(3, 1f, 1f, 0f);

        VertexAttributeView nrm = mesh.attribute(AttributeSemantic.NORMAL, 0);
        nrm.set3f(0, 0f, 0f, 1f);
        nrm.set3f(1, 0f, 0f, 1f);
        nrm.set3f(2, 0f, 0f, 1f);
        nrm.set3f(3, 0f, 0f, 1f);

        VertexAttributeView uv = mesh.attribute(AttributeSemantic.UV, 0);
        uv.set2f(0, 0f, 0f);
        uv.set2f(1, 1f, 0f);
        uv.set2f(2, 0f, 1f);
        uv.set2f(3, 1f, 1f);
        mesh.setBounds(new Boundsf(
            new Aabbf(0f, 0f, 0f, 1f, 1f, 0f),
            new Spheref(0.5f, 0.5f, 0f, (float) Math.sqrt(0.5f))
        ));
        return mesh;
    }
}
