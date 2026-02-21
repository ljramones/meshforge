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
import org.meshforge.pack.packer.MeshPacker;
import org.meshforge.pack.spec.PackSpec;

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
}
