package org.dynamisengine.meshforge.test;

import org.junit.jupiter.api.Test;
import org.dynamisengine.meshforge.api.Meshes;
import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshPipelinePresetsTest {
    @Test
    void realtimeFastWorksWithoutUvAndSkipsTangents() {
        var mesh = Meshes.cube(1.0f);
        mesh = Pipelines.realtimeFast(mesh);

        assertTrue(mesh.has(AttributeSemantic.NORMAL, 0));
        assertFalse(mesh.has(AttributeSemantic.TANGENT, 0));
        assertNotNull(mesh.boundsOrNull());
    }

    @Test
    void realtimeFastPackPresetProducesPackedMesh() {
        var mesh = Pipelines.realtimeFast(Meshes.cube(1.0f));
        PackedMesh packed = MeshPacker.pack(mesh, Packers.realtimeFast());
        assertNotNull(packed);
        assertNotNull(packed.vertexBuffer());
    }
}

