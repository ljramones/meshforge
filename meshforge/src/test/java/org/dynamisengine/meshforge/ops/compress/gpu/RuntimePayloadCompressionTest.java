package org.dynamisengine.meshforge.ops.compress.gpu;

import org.dynamisengine.meshforge.core.bounds.Aabbf;
import org.dynamisengine.meshforge.ops.cull.gpu.GpuMeshletVisibilityPayload;
import org.dynamisengine.meshforge.ops.cull.gpu.MeshletVisibilityUploadPrep;
import org.dynamisengine.meshforge.ops.lod.MeshletLodLevelMetadata;
import org.dynamisengine.meshforge.ops.lod.MeshletLodMetadata;
import org.dynamisengine.meshforge.ops.lod.gpu.GpuMeshletLodPayload;
import org.dynamisengine.meshforge.ops.lod.gpu.MeshletLodUploadPrep;
import org.dynamisengine.meshforge.ops.streaming.MeshletStreamUnitMetadata;
import org.dynamisengine.meshforge.ops.streaming.MeshletStreamingMetadata;
import org.dynamisengine.meshforge.ops.streaming.gpu.GpuMeshletStreamingPayload;
import org.dynamisengine.meshforge.ops.streaming.gpu.MeshletStreamingUploadPrep;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimePayloadCompressionTest {
    @Test
    void roundTripsDeflateCompression() {
        byte[] raw = new byte[32 * 1024];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) ((i * 7) & 0xFF);
        }

        byte[] compressed = RuntimePayloadCompression.compress(raw, RuntimePayloadCompressionMode.DEFLATE);
        byte[] decompressed = RuntimePayloadCompression.decompress(
            compressed,
            raw.length,
            RuntimePayloadCompressionMode.DEFLATE
        );

        assertArrayEquals(raw, decompressed);
    }

    @Test
    void feasibilityOnMeshletRuntimePayloadShapes() {
        byte[] visibilityBytes = visibilityPayloadBytes(20_000);
        byte[] lodBytes = lodPayloadBytes(12);
        byte[] streamingBytes = streamingPayloadBytes(2_048);

        Feasibility visibility = measure("visibility", visibilityBytes);
        Feasibility lod = measure("lod", lodBytes);
        Feasibility streaming = measure("streaming", streamingBytes);

        System.out.println(visibility.format());
        System.out.println(lod.format());
        System.out.println(streaming.format());

        // These payloads should generally be compressible due to structural repetition.
        assertTrue(visibility.ratio < 1.0, "visibility payload should compress");
        assertTrue(streaming.ratio < 1.0, "streaming payload should compress");
        assertTrue(lod.ratio <= 1.10, "lod payload should not expand materially");
    }

    private static byte[] visibilityPayloadBytes(int meshletCount) {
        List<Aabbf> bounds = new ArrayList<>(meshletCount);
        for (int i = 0; i < meshletCount; i++) {
            float x = (i % 512) * 0.25f;
            float y = ((i / 512) % 256) * 0.125f;
            float z = (i % 97) * 0.0625f;
            bounds.add(new Aabbf(x, y, z, x + 0.5f, y + 0.5f, z + 0.5f));
        }
        GpuMeshletVisibilityPayload payload = MeshletVisibilityUploadPrep.fromMeshletBounds(bounds);
        return toArray(payload.toBoundsByteBuffer());
    }

    private static byte[] lodPayloadBytes(int levels) {
        List<MeshletLodLevelMetadata> levelMetadata = new ArrayList<>(levels);
        int meshletStart = 0;
        int meshletCount = 4096;
        for (int i = 0; i < levels; i++) {
            levelMetadata.add(new MeshletLodLevelMetadata(i, meshletStart, Math.max(16, meshletCount), i * 0.125f));
            meshletStart += Math.max(16, meshletCount);
            meshletCount /= 2;
        }
        MeshletLodMetadata metadata = new MeshletLodMetadata(levelMetadata);
        GpuMeshletLodPayload payload = MeshletLodUploadPrep.fromMetadata(metadata);
        return toArray(payload.toLevelsByteBuffer());
    }

    private static byte[] streamingPayloadBytes(int units) {
        List<MeshletStreamUnitMetadata> unitMetadata = new ArrayList<>(units);
        int meshletStart = 0;
        int payloadOffset = 0;
        for (int i = 0; i < units; i++) {
            int meshletCount = 32 + (i % 4) * 16;
            int payloadSize = 2048 + (i % 8) * 512;
            unitMetadata.add(new MeshletStreamUnitMetadata(i, meshletStart, meshletCount, payloadOffset, payloadSize));
            meshletStart += meshletCount;
            payloadOffset += payloadSize;
        }
        MeshletStreamingMetadata metadata = new MeshletStreamingMetadata(unitMetadata);
        GpuMeshletStreamingPayload payload = MeshletStreamingUploadPrep.fromMetadata(metadata);
        return toArray(payload.toUnitsByteBuffer());
    }

    private static Feasibility measure(String label, byte[] rawBytes) {
        final int warmup = 4;
        final int runs = 12;

        for (int i = 0; i < warmup; i++) {
            byte[] compressed = RuntimePayloadCompression.compress(rawBytes, RuntimePayloadCompressionMode.DEFLATE);
            RuntimePayloadCompression.decompress(compressed, rawBytes.length, RuntimePayloadCompressionMode.DEFLATE);
        }

        long encodeNanos = 0L;
        long decodeNanos = 0L;
        int compressedBytes = 0;
        for (int i = 0; i < runs; i++) {
            long t0 = System.nanoTime();
            byte[] compressed = RuntimePayloadCompression.compress(rawBytes, RuntimePayloadCompressionMode.DEFLATE);
            long t1 = System.nanoTime();
            byte[] decompressed = RuntimePayloadCompression.decompress(
                compressed,
                rawBytes.length,
                RuntimePayloadCompressionMode.DEFLATE
            );
            long t2 = System.nanoTime();

            assertArrayEquals(rawBytes, decompressed);
            encodeNanos += (t1 - t0);
            decodeNanos += (t2 - t1);
            compressedBytes = compressed.length;
        }

        return new Feasibility(
            label,
            rawBytes.length,
            compressedBytes,
            ((double) compressedBytes) / Math.max(1, rawBytes.length),
            Duration.ofNanos(encodeNanos / runs).toMillis() + ((encodeNanos / runs) % 1_000_000) / 1_000_000.0,
            Duration.ofNanos(decodeNanos / runs).toMillis() + ((decodeNanos / runs) % 1_000_000) / 1_000_000.0
        );
    }

    private static byte[] toArray(ByteBuffer buffer) {
        ByteBuffer view = buffer.asReadOnlyBuffer();
        byte[] out = new byte[view.remaining()];
        view.get(out);
        return out;
    }

    private record Feasibility(
        String label,
        int rawBytes,
        int compressedBytes,
        double ratio,
        double encodeMs,
        double decodeMs
    ) {
        String format() {
            return String.format(
                Locale.ROOT,
                "compression[%s]: raw=%d compressed=%d ratio=%.3f encodeMs=%.3f decodeMs=%.3f",
                label,
                rawBytes,
                compressedBytes,
                ratio,
                encodeMs,
                decodeMs
            );
        }
    }
}

