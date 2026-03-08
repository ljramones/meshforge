package org.dynamisengine.meshforge.gpu.cache;

import org.dynamisengine.meshforge.api.Meshes;
import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class RuntimeGeometryCacheIOTest {
    @Test
    void roundTripsPayload() throws Exception {
        MeshData mesh = Meshes.cube(1.0f);
        PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);

        Path tmp = Files.createTempFile("meshforge-runtime-geo-", ".mfgc");
        RuntimeGeometryCacheIO.write(tmp, payload);
        RuntimeGeometryPayload loaded = RuntimeGeometryCacheIO.read(tmp);

        assertEquals(payload.vertexCount(), loaded.vertexCount());
        assertEquals(payload.indexCount(), loaded.indexCount());
        assertEquals(payload.layout().strideBytes(), loaded.layout().strideBytes());
        assertEquals(payload.submeshes().size(), loaded.submeshes().size());
        assertNotNull(loaded.vertexBytes());
    }
}
