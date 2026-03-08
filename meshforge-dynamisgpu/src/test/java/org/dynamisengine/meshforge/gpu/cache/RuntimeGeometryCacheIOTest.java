package org.dynamisengine.meshforge.gpu.cache;

import org.dynamisengine.meshforge.api.Meshes;
import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void rejectsUnsupportedVersion() throws Exception {
        MeshData mesh = Meshes.cube(1.0f);
        PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);
        Path tmp = Files.createTempFile("meshforge-runtime-geo-version-", ".mfgc");
        RuntimeGeometryCacheIO.write(tmp, payload);

        try (RandomAccessFile raf = new RandomAccessFile(tmp.toFile(), "rw")) {
            raf.seek(4); // version field
            raf.writeInt(999);
        }

        assertThrows(IOException.class, () -> RuntimeGeometryCacheIO.read(tmp));
    }

    @Test
    void rejectsUnsupportedFlags() throws Exception {
        MeshData mesh = Meshes.cube(1.0f);
        PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);
        Path tmp = Files.createTempFile("meshforge-runtime-geo-flags-", ".mfgc");
        RuntimeGeometryCacheIO.write(tmp, payload);

        try (RandomAccessFile raf = new RandomAccessFile(tmp.toFile(), "rw")) {
            raf.seek(9); // flags field (magic[4] + version[4] + endian[1])
            raf.writeInt(0x40);
        }

        assertThrows(IOException.class, () -> RuntimeGeometryCacheIO.read(tmp));
    }

    @Test
    void rejectsLayoutHashMismatch() throws Exception {
        MeshData mesh = Meshes.cube(1.0f);
        PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);
        Path tmp = Files.createTempFile("meshforge-runtime-geo-lhash-", ".mfgc");
        RuntimeGeometryCacheIO.write(tmp, payload);

        try (RandomAccessFile raf = new RandomAccessFile(tmp.toFile(), "rw")) {
            raf.seek(13); // layoutHash starts after magic/version/endian/flags
            raf.writeLong(0xDEADBEEFL);
        }

        assertThrows(IOException.class, () -> RuntimeGeometryCacheIO.read(tmp));
    }

    @Test
    void rejectsTruncatedPayload() throws Exception {
        MeshData mesh = Meshes.cube(1.0f);
        PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromPackedMesh(packed);
        Path tmp = Files.createTempFile("meshforge-runtime-geo-trunc-", ".mfgc");
        RuntimeGeometryCacheIO.write(tmp, payload);

        byte[] bytes = Files.readAllBytes(tmp);
        byte[] truncated = new byte[Math.max(1, bytes.length / 2)];
        System.arraycopy(bytes, 0, truncated, 0, truncated.length);
        Files.write(tmp, truncated);

        assertThrows(IOException.class, () -> RuntimeGeometryCacheIO.read(tmp));
    }
}
