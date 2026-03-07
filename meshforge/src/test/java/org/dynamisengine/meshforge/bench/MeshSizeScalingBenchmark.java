package org.dynamisengine.meshforge.bench;

import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 4, time = 800, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
public class MeshSizeScalingBenchmark {

    @State(Scope.Thread)
    public static class BenchState {
        @Param({"64", "128", "256"})
        int cells;

        MeshData template;
        MeshData working;

        @Setup(Level.Trial)
        public void trialSetup() {
            template = BenchmarkFixtures.createRichGrid(cells, cells);
            int[] shuffled = template.indicesOrNull().clone();
            BenchmarkFixtures.shuffleTriangles(shuffled, 4242L);
            template.setIndices(shuffled);
        }

        @Setup(Level.Invocation)
        public void invocationSetup() {
            working = BenchmarkFixtures.copyOf(template);
        }
    }

    @Benchmark
    public void pipelineRealtime(BenchState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Pipelines.realtimeOps(state.working));
        bh.consume(out.boundsOrNull());
    }

    @Benchmark
    public void pipelineRealtimeFast(BenchState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Pipelines.realtimeFastOps(state.working));
        bh.consume(out.boundsOrNull());
    }

    @Benchmark
    public void packRealtime(BenchState state, Blackhole bh) {
        PackedMesh packed = MeshPacker.pack(state.working, PackSpec.realtime());
        bh.consume(packed.vertexBuffer().capacity());
    }
}

