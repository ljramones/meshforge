package org.dynamisengine.meshforge.mgi;

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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MgiTrustedRuntimeFastPathTest {

    @Test
    void trustedAssetUsesFastPathWhenEnabled() throws Exception {
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        byte[] bytes = codec.write(sampleTrustedMeshData());
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(bytes);

        Pipelines.RuntimeStageProfile profile = new Pipelines.RuntimeStageProfile();
        MeshData out = Pipelines.realtimeFastProfiled(
            decoded.meshData(),
            profile,
            true,
            decoded.trustedCanonical(),
            decoded.degenerateFree(),
            decoded.hasPrebakedBounds()
        );

        assertTrue(decoded.metadataPresent());
        assertTrue(decoded.trustedCanonical());
        assertTrue(decoded.degenerateFree());
        assertTrue(decoded.hasPrebakedBounds());
        assertTrue(profile.trustedFastPathUsed());
        assertNotNull(out.boundsOrNull());
    }

    @Test
    void missingMetadataFallsBackToSafePath() throws Exception {
        MgiStaticMeshCodec staticCodec = new MgiStaticMeshCodec();
        MgiStaticMesh mesh = new MgiStaticMesh(
            positions(),
            null,
            null,
            new MgiAabb(0f, 0f, 0f, 1f, 1f, 0f),
            null,
            null,
            null,
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(0, 3, 0))
        );
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(staticCodec.write(mesh));

        Pipelines.RuntimeStageProfile profile = new Pipelines.RuntimeStageProfile();
        Pipelines.realtimeFastProfiled(
            decoded.meshData(),
            profile,
            true,
            decoded.trustedCanonical(),
            decoded.degenerateFree(),
            decoded.hasPrebakedBounds()
        );

        assertFalse(decoded.metadataPresent());
        assertFalse(profile.trustedFastPathUsed());
        assertTrue(profile.removeDegeneratesNs() > 0L);
        assertTrue(profile.boundsNs() > 0L);
    }

    @Test
    void missingBoundsFallsBackToSafePath() throws Exception {
        MgiStaticMeshCodec staticCodec = new MgiStaticMeshCodec();
        MgiStaticMesh mesh = new MgiStaticMesh(
            positions(),
            null,
            null,
            null,
            new MgiCanonicalMetadata(
                3,
                3,
                MgiCanonicalMetadata.FLAG_DEGENERATE_FREE | MgiCanonicalMetadata.FLAG_TRUSTED_CANONICAL
            ),
            null,
            null,
            new int[] {0, 1, 2},
            List.of(new MgiSubmeshRange(0, 3, 0))
        );
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(staticCodec.write(mesh));

        Pipelines.RuntimeStageProfile profile = new Pipelines.RuntimeStageProfile();
        Pipelines.realtimeFastProfiled(
            decoded.meshData(),
            profile,
            true,
            decoded.trustedCanonical(),
            decoded.degenerateFree(),
            decoded.hasPrebakedBounds()
        );

        assertTrue(decoded.metadataPresent());
        assertFalse(decoded.hasPrebakedBounds());
        assertFalse(profile.trustedFastPathUsed());
        assertTrue(profile.removeDegeneratesNs() > 0L);
        assertTrue(profile.boundsNs() > 0L);
    }

    @Test
    void trustedModeDisabledUsesSafePath() throws Exception {
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        byte[] bytes = codec.write(sampleTrustedMeshData());
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(bytes);

        Pipelines.RuntimeStageProfile profile = new Pipelines.RuntimeStageProfile();
        Pipelines.realtimeFastProfiled(
            decoded.meshData(),
            profile,
            false,
            decoded.trustedCanonical(),
            decoded.degenerateFree(),
            decoded.hasPrebakedBounds()
        );

        assertFalse(profile.trustedFastPathUsed());
        assertTrue(profile.removeDegeneratesNs() > 0L);
        assertTrue(profile.boundsNs() > 0L);
    }

    private static MeshData sampleTrustedMeshData() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, 0))
        );
        VertexAttributeView pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        pos.set3f(0, 0f, 0f, 0f);
        pos.set3f(1, 1f, 0f, 0f);
        pos.set3f(2, 0f, 1f, 0f);
        mesh.setBounds(new Boundsf(
            new Aabbf(0f, 0f, 0f, 1f, 1f, 0f),
            new Spheref(0.5f, 0.5f, 0f, (float) Math.sqrt(0.5f))
        ));
        return mesh;
    }

    private static float[] positions() {
        return new float[] {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        };
    }
}
