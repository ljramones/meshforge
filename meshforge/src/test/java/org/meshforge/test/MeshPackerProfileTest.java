package org.meshforge.test;

import org.junit.jupiter.api.Test;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;
import org.meshforge.pack.packer.MeshPacker;
import org.meshforge.pack.packer.PackProfile;
import org.meshforge.pack.spec.PackSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshPackerProfileTest {
    @Test
    void packProfileCapturesDurationsAndCounts() {
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
        PackProfile profile = new PackProfile();

        MeshPacker.pack(mesh, PackSpec.realtime(), profile);

        assertEquals(mesh.vertexCount(), profile.vertexCount());
        assertEquals(mesh.indicesOrNull().length, profile.indexCount());
        assertTrue(profile.strideBytes() > 0);
        assertTrue(profile.resolveAttributesNs() > 0L);
        assertTrue(profile.layoutNs() > 0L);
        assertTrue(profile.vertexWriteNs() > 0L);
        assertTrue(profile.positionWriteNs() >= 0L);
        assertTrue(profile.normalPackNs() >= 0L);
        assertTrue(profile.tangentPackNs() >= 0L);
        assertTrue(profile.uvPackNs() >= 0L);
        assertTrue(profile.colorPackNs() >= 0L);
        assertTrue(profile.skinPackNs() >= 0L);
        assertTrue(profile.indexPackNs() > 0L);
        assertTrue(profile.submeshCopyNs() > 0L);
        assertTrue(profile.totalNs() > 0L);
        assertTrue(profile.vertexWriteNs() >= profile.positionWriteNs());
    }
}
