package org.dynamisengine.meshforge.loader.bench;

import org.dynamisengine.meshforge.loader.gltf.read.MeshoptCompressionFilter;
import org.dynamisengine.meshforge.loader.gltf.read.MeshoptCompressionMode;
import org.dynamisengine.meshforge.loader.gltf.read.MeshoptDecodeRequest;
import org.dynamisengine.meshforge.loader.gltf.read.MeshoptDecodeResult;
import org.dynamisengine.meshforge.loader.gltf.read.MeshoptDecoder;
import org.dynamisengine.vectrix.gpu.OctaNormal;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 700, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
public class MeshoptDecoderBaselineBenchmark {
    private static final int BATCH_SIZE = 64;

    @State(Scope.Benchmark)
    public static class DecoderState {
        @Param({"SMALL", "MEDIUM", "LARGE", "ATTRIBUTE_HEAVY"})
        String workload;

        @Param({"NONE", "OCTAHEDRAL"})
        String filter;

        MeshoptDecodeRequest request;

        @Setup
        public void setup() {
            boolean octa = "OCTAHEDRAL".equals(filter);
            int count = switch (workload) {
                case "SMALL" -> 1_024;
                case "MEDIUM" -> 16_384;
                case "LARGE" -> 131_072;
                case "ATTRIBUTE_HEAVY" -> 65_536;
                default -> throw new IllegalArgumentException("Unknown workload: " + workload);
            };
            int stride = octa ? 4 : ("ATTRIBUTE_HEAVY".equals(workload) ? 32 : 16);
            byte[] payload = octa ? createOctaPayload(count) : createRandomPayload(count, stride);
            byte[] compressed = lz4LiteralBlock(payload);
            request = new MeshoptDecodeRequest(
                compressed,
                payload.length,
                count,
                stride,
                MeshoptCompressionMode.ATTRIBUTES,
                octa ? MeshoptCompressionFilter.OCTAHEDRAL : MeshoptCompressionFilter.NONE
            );
        }
    }

    @Benchmark
    public void decode(DecoderState state, Blackhole bh) {
        MeshoptDecodeResult result = MeshoptDecoder.decode(state.request);
        bh.consume(result.byteStride());
        bh.consume(result.data().length);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void decodeBatch(DecoderState state, Blackhole bh) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            MeshoptDecodeResult result = MeshoptDecoder.decode(state.request);
            bh.consume(result.count());
        }
    }

    private static byte[] createRandomPayload(int count, int stride) {
        byte[] out = new byte[count * stride];
        Random random = new Random(1234);
        random.nextBytes(out);
        return out;
    }

    private static byte[] createOctaPayload(int count) {
        byte[] out = new byte[count * 4];
        ByteBuffer bb = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        Random random = new Random(99);
        for (int i = 0; i < count; i++) {
            float x = (random.nextFloat() * 2.0f) - 1.0f;
            float y = (random.nextFloat() * 2.0f) - 1.0f;
            float z = (random.nextFloat() * 2.0f) - 1.0f;
            int packed = OctaNormal.encodeSnorm16(x, y, z);
            bb.putShort((short) (packed & 0xFFFF));
            bb.putShort((short) ((packed >>> 16) & 0xFFFF));
        }
        return out;
    }

    private static byte[] lz4LiteralBlock(byte[] payload) {
        int len = payload.length;
        if (len < 15) {
            byte[] out = new byte[1 + len];
            out[0] = (byte) (len << 4);
            System.arraycopy(payload, 0, out, 1, len);
            return out;
        }
        int ext = len - 15;
        int extCount = (ext / 255) + 1;
        byte[] out = new byte[1 + extCount + len];
        out[0] = (byte) 0xF0;
        int pos = 1;
        while (ext >= 255) {
            out[pos++] = (byte) 255;
            ext -= 255;
        }
        out[pos++] = (byte) ext;
        System.arraycopy(payload, 0, out, pos, len);
        return out;
    }
}
