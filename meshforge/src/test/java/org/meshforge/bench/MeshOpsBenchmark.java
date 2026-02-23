package org.meshforge.bench;

import org.meshforge.api.Ops;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshPipeline;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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
public class MeshOpsBenchmark {

    @State(Scope.Thread)
    public static class PositionState {
        MeshData template;
        MeshData working;

        @Setup(Level.Trial)
        public void trialSetup() {
            template = BenchmarkFixtures.createPositionGrid(256, 256);
        }

        @Setup(Level.Iteration)
        public void invocationSetup() {
            working = BenchmarkFixtures.copyOf(template);
        }
    }

    @State(Scope.Thread)
    public static class DegenerateState {
        MeshData template;
        MeshData working;

        @Setup(Level.Trial)
        public void trialSetup() {
            template = BenchmarkFixtures.createPositionGridWithDegenerates(256, 256, 8);
        }

        @Setup(Level.Iteration)
        public void invocationSetup() {
            working = BenchmarkFixtures.copyOf(template);
        }
    }

    @State(Scope.Thread)
    public static class RichState {
        MeshData template;
        MeshData working;

        @Setup(Level.Trial)
        public void trialSetup() {
            template = BenchmarkFixtures.createRichGrid(256, 256);
        }

        @Setup(Level.Iteration)
        public void invocationSetup() {
            working = BenchmarkFixtures.copyOf(template);
        }
    }

    @State(Scope.Thread)
    public static class CacheState {
        MeshData template;
        MeshData working;

        @Setup(Level.Trial)
        public void trialSetup() {
            template = BenchmarkFixtures.createPositionGrid(256, 256);
            int[] shuffled = template.indicesOrNull().clone();
            BenchmarkFixtures.shuffleTriangles(shuffled, 1337L);
            template.setIndices(shuffled);
        }

        @Setup(Level.Iteration)
        public void invocationSetup() {
            working = BenchmarkFixtures.copyOf(template);
        }
    }

    @Benchmark
    public void validate(PositionState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Ops.validate());
        bh.consume(out.vertexCount());
    }

    @Benchmark
    public void removeDegenerates(DegenerateState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Ops.removeDegenerates());
        bh.consume(out.indicesOrNull().length);
    }

    @Benchmark
    public void weld(PositionState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Ops.weld(1.0e-6f));
        bh.consume(out.vertexCount());
    }

    @Benchmark
    public void recalculateNormals(PositionState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Ops.normals(60f));
        bh.consume(out.attributeFormats().size());
    }

    @Benchmark
    public void recalculateTangents(RichState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Ops.tangents());
        bh.consume(out.attributeFormats().size());
    }

    @Benchmark
    public void optimizeVertexCache(CacheState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Ops.optimizeVertexCache());
        bh.consume(out.indicesOrNull().length);
    }

    @Benchmark
    public void computeBounds(PositionState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Ops.bounds());
        bh.consume(out.boundsOrNull());
    }
}
