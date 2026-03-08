package org.dynamisengine.meshforge.gpu;

import org.dynamisengine.meshforge.api.Meshes;
import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class MeshForgeGpuBridgeTest {
    @Test
    void buildsUploadPlanFromPackedMesh() {
        MeshData mesh = Meshes.cube(1.0f);
        PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());

        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);
        GpuGeometryUploadPlan plan = MeshForgeGpuBridge.buildUploadPlan(payload);

        assertNotNull(plan.vertexBinding());
        assertEquals(payload.layout().strideBytes(), plan.vertexBinding().strideBytes());
        assertEquals(payload.submeshes().size(), plan.submeshes().size());
    }
}
