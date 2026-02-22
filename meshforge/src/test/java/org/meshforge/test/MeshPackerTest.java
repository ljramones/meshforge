package org.meshforge.test;

import org.junit.jupiter.api.Test;
import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;
import org.meshforge.pack.buffer.PackedMesh;
import org.meshforge.pack.buffer.MeshletBuffers;
import org.meshforge.pack.packer.MeshPacker;
import org.meshforge.pack.spec.PackSpec;
import org.vectrix.core.Vector3f;
import org.vectrix.gpu.OctaNormal;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshPackerTest {

    @Test
    void realtimeLayoutHasExpectedOffsetsAndStride() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .add(AttributeSemantic.COLOR, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.JOINTS, 0, VertexFormat.I32x4)
            .add(AttributeSemantic.WEIGHTS, 0, VertexFormat.F32x4)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            4,
            new int[] {0, 1, 2, 0, 2, 3},
            List.of(new Submesh(0, 6, "m"))
        );

        PackedMesh packed = MeshPacker.pack(mesh, PackSpec.realtime());
        assertEquals(48, packed.layout().strideBytes());

        assertEquals(0, packed.layout().entry(new AttributeKey(AttributeSemantic.POSITION, 0)).offsetBytes());
        assertEquals(12, packed.layout().entry(new AttributeKey(AttributeSemantic.NORMAL, 0)).offsetBytes());
        assertEquals(16, packed.layout().entry(new AttributeKey(AttributeSemantic.TANGENT, 0)).offsetBytes());
        assertEquals(20, packed.layout().entry(new AttributeKey(AttributeSemantic.UV, 0)).offsetBytes());
        assertEquals(24, packed.layout().entry(new AttributeKey(AttributeSemantic.COLOR, 0)).offsetBytes());
        assertEquals(28, packed.layout().entry(new AttributeKey(AttributeSemantic.JOINTS, 0)).offsetBytes());
        assertEquals(32, packed.layout().entry(new AttributeKey(AttributeSemantic.WEIGHTS, 0)).offsetBytes());

        assertNotNull(packed.indexBuffer());
        assertEquals(PackedMesh.IndexType.UINT16, packed.indexBuffer().type());
    }

    @Test
    void debugLayoutHasExpectedStride() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .add(AttributeSemantic.COLOR, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.JOINTS, 0, VertexFormat.I32x4)
            .add(AttributeSemantic.WEIGHTS, 0, VertexFormat.F32x4)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );

        PackedMesh packed = MeshPacker.pack(mesh, PackSpec.debug());
        assertEquals(96, packed.layout().strideBytes());
        assertEquals(PackedMesh.IndexType.UINT16, packed.indexBuffer().type());
    }

    @Test
    void indexPolicyFallsBackToUint32() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            70000,
            new int[] {0, 1, 69999},
            List.of(new Submesh(0, 3, "m"))
        );

        PackedMesh packed = MeshPacker.pack(mesh, PackSpec.realtime());
        assertNotNull(packed.indexBuffer());
        assertEquals(PackedMesh.IndexType.UINT32, packed.indexBuffer().type());
    }

    @Test
    void missingNormalsFailsWhenRequired() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );

        PackSpec spec = PackSpec.builder()
            .layout(PackSpec.LayoutMode.INTERLEAVED)
            .alignment(16)
            .indexPolicy(PackSpec.IndexPolicy.AUTO_16_IF_POSSIBLE)
            .target(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .failIfMissingNormals(true)
            .build();

        assertThrows(IllegalStateException.class, () -> MeshPacker.pack(mesh, spec));
    }

    @Test
    void realtimeWithMeshletsBuildsDescriptorsWithinLimits() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        int vertexCount = 16;
        int[] indices = {
            0, 1, 2, 0, 2, 3,
            4, 5, 6, 4, 6, 7,
            8, 9, 10, 8, 10, 11,
            12, 13, 14, 12, 14, 15
        };
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            vertexCount,
            indices,
            List.of(new Submesh(0, indices.length, "m"))
        );

        float[] pos = mesh.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        assertNotNull(pos);
        for (int i = 0; i < vertexCount; i++) {
            pos[i * 3] = i;
            pos[i * 3 + 1] = 0.0f;
            pos[i * 3 + 2] = 0.0f;
        }

        PackSpec spec = PackSpec.builder()
            .layout(PackSpec.LayoutMode.INTERLEAVED)
            .alignment(16)
            .indexPolicy(PackSpec.IndexPolicy.AUTO_16_IF_POSSIBLE)
            .target(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .meshletsEnabled(true)
            .maxMeshletVertices(6)
            .maxMeshletTriangles(2)
            .build();

        PackedMesh packed = MeshPacker.pack(mesh, spec);
        assertTrue(packed.hasMeshlets());
        var meshlets = packed.meshletsOrNull();
        assertNotNull(meshlets);
        assertTrue(meshlets.meshletCount() >= 4);
        assertNotNull(packed.meshletDescriptorBufferOrNull());
        int expectedStride = MeshletBuffers.descriptorStrideBytes(16);
        assertEquals(expectedStride, packed.meshletDescriptorStrideBytes());
        assertEquals(meshlets.meshletCount() * expectedStride, packed.meshletDescriptorBufferOrNull().capacity());

        int totalTri = 0;
        for (int i = 0; i < meshlets.meshletCount(); i++) {
            var m = meshlets.meshlet(i);
            assertTrue(m.triangleCount() <= 2);
            assertTrue(m.uniqueVertexCount() <= 6);
            totalTri += m.triangleCount();
        }
        assertEquals(indices.length / 3, totalTri);
    }

    @Test
    void realtimeWithOctaNormalsPacksExpectedFormatAndRoundTrips() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );

        mesh.attribute(AttributeSemantic.POSITION, 0).set3f(0, 0.0f, 0.0f, 0.0f);
        mesh.attribute(AttributeSemantic.POSITION, 0).set3f(1, 1.0f, 0.0f, 0.0f);
        mesh.attribute(AttributeSemantic.POSITION, 0).set3f(2, 0.0f, 1.0f, 0.0f);
        mesh.attribute(AttributeSemantic.NORMAL, 0).set3f(0, 0.0f, 0.0f, 1.0f);
        mesh.attribute(AttributeSemantic.NORMAL, 0).set3f(1, 0.57735f, 0.57735f, 0.57735f);
        mesh.attribute(AttributeSemantic.NORMAL, 0).set3f(2, -0.2f, 0.95f, 0.24f);

        PackedMesh packed = MeshPacker.pack(mesh, PackSpec.realtimeWithOctaNormals());

        var normalEntry = packed.layout().entry(new AttributeKey(AttributeSemantic.NORMAL, 0));
        assertNotNull(normalEntry);
        assertEquals(VertexFormat.OCTA_SNORM16x2, normalEntry.format());
        assertEquals(PackSpec.NormalPacking.OCTA_SNORM16x2, PackSpec.realtimeWithOctaNormals().normalPacking());

        ByteBuffer vb = packed.vertexBuffer();
        int stride = packed.layout().strideBytes();
        int normalOff = normalEntry.offsetBytes();

        Vector3f decoded = new Vector3f();
        int packed0 = vb.getInt(normalOff);
        OctaNormal.decodeSnorm16(packed0, decoded);
        assertEquals(0.0f, decoded.x(), 0.02f);
        assertEquals(0.0f, decoded.y(), 0.02f);
        assertEquals(1.0f, decoded.z(), 0.02f);

        int packed1 = vb.getInt(stride + normalOff);
        OctaNormal.decodeSnorm16(packed1, decoded);
        assertEquals(0.57735f, decoded.x(), 0.03f);
        assertEquals(0.57735f, decoded.y(), 0.03f);
        assertEquals(0.57735f, decoded.z(), 0.03f);
    }
}
