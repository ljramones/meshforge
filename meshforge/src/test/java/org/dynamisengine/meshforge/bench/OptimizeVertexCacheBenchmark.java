package org.dynamisengine.meshforge.bench;

import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.ops.optimize.CacheMetrics;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 800, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview"})
public class OptimizeVertexCacheBenchmark {

    @State(Scope.Thread)
    public static class BenchState {
        MeshData template;
        MeshData working;

        @Setup(Level.Trial)
        public void trialSetup() {
            template = BenchmarkFixtures.createPositionGrid(256, 256);
            int[] shuffled = template.indicesOrNull().clone();
            BenchmarkFixtures.shuffleTriangles(shuffled, 1337L);
            template.setIndices(shuffled);
        }

        @Setup(Level.Invocation)
        public void invocationSetup() {
            working = BenchmarkFixtures.copyOf(template);
        }
    }

    @Benchmark
    public double optimizeAndMeasureAcmr(BenchState state) {
        MeshData optimized = MeshPipeline.run(state.working, Ops.optimizeVertexCache());
        return CacheMetrics.acmr(optimized.indicesOrNull(), 32);
    }
}
