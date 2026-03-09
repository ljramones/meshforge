package org.dynamisengine.meshforge.mgi;

import org.dynamisengine.meshforge.ops.tessellation.TessellationMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class MgiTessellationRuntimeDecodeTest {

    @Test
    void runtimeDecodeExposesTessellationMetadataWhenPresent() throws Exception {
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
            null,
            null,
            null,
            null,
            new MgiTessellationData(List.of(new MgiTessellationRegion(0, 0, 6, 3, 1.5f, 2))),
            new int[] {0, 1, 2, 0, 2, 3},
            List.of(new MgiSubmeshRange(0, 6, 0))
        );

        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        byte[] bytes = new MgiStaticMeshCodec().write(staticMesh);
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(bytes);

        assertNotNull(decoded.tessellationDataOrNull());
        assertEquals(1, decoded.tessellationDataOrNull().regions().size());

        TessellationMetadata handoff = decoded.tessellationMetadataOrNull();
        assertNotNull(handoff);
        assertEquals(1, handoff.regionCount());
        assertEquals(3, handoff.regions().getFirst().patchControlPoints());
        assertEquals(1.5f, handoff.regions().getFirst().tessLevel());
    }

    @Test
    void runtimeDecodeCanonicalMeshExposesDefaultTessellationHandoff() throws Exception {
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(codec.write(sampleTriangle()));

        assertNotNull(decoded.rayTracingMetadataOrNull());
        assertNotNull(decoded.tessellationDataOrNull());
        assertNotNull(decoded.tessellationMetadataOrNull());
        assertEquals(1, decoded.tessellationMetadataOrNull().regionCount());
    }

    private static org.dynamisengine.meshforge.core.mesh.MeshData sampleTriangle() {
        org.dynamisengine.meshforge.core.attr.VertexSchema schema = org.dynamisengine.meshforge.core.attr.VertexSchema.builder()
            .add(org.dynamisengine.meshforge.core.attr.AttributeSemantic.POSITION, 0,
                org.dynamisengine.meshforge.core.attr.VertexFormat.F32x3)
            .build();
        org.dynamisengine.meshforge.core.mesh.MeshData mesh = new org.dynamisengine.meshforge.core.mesh.MeshData(
            org.dynamisengine.meshforge.core.topology.Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(new org.dynamisengine.meshforge.core.mesh.Submesh(0, 3, 0))
        );
        mesh.attribute(org.dynamisengine.meshforge.core.attr.AttributeSemantic.POSITION, 0).set3f(0, 0f, 0f, 0f);
        mesh.attribute(org.dynamisengine.meshforge.core.attr.AttributeSemantic.POSITION, 0).set3f(1, 1f, 0f, 0f);
        mesh.attribute(org.dynamisengine.meshforge.core.attr.AttributeSemantic.POSITION, 0).set3f(2, 0f, 1f, 0f);
        return mesh;
    }
}
