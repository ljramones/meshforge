package org.meshforge.bench;

import org.meshforge.core.mesh.MeshData;
import org.meshforge.pack.buffer.PackedMesh;
import org.meshforge.pack.packer.MeshPacker;
import org.meshforge.pack.spec.PackSpec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 800, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
public class MeshPackerBenchmark {

    @State(Scope.Benchmark)
    public static class BenchState {
        MeshData richMesh;

        @Setup
        public void setup() {
            richMesh = BenchmarkFixtures.createRichGrid(256, 256);
        }
    }

    @Benchmark
    public void packRealtime(BenchState state, Blackhole bh) {
        PackedMesh packed = MeshPacker.pack(state.richMesh, PackSpec.realtime());
        bh.consume(packed.layout().strideBytes());
        bh.consume(packed.vertexBuffer().capacity());
    }

    @Benchmark
    public void packRealtimeOctaNormals(BenchState state, Blackhole bh) {
        PackedMesh packed = MeshPacker.pack(state.richMesh, PackSpec.realtimeWithOctaNormals());
        bh.consume(packed.layout().strideBytes());
        bh.consume(packed.vertexBuffer().capacity());
    }

    @Benchmark
    public void packDebug(BenchState state, Blackhole bh) {
        PackedMesh packed = MeshPacker.pack(state.richMesh, PackSpec.debug());
        bh.consume(packed.layout().strideBytes());
        bh.consume(packed.vertexBuffer().capacity());
    }
}
