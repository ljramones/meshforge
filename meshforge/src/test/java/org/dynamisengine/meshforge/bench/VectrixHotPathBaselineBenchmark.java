package org.dynamisengine.meshforge.bench;

import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.ops.generate.RecalculateTangentsOp;
import org.dynamisengine.meshforge.ops.pipeline.MeshContext;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimeMeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimePackPlan;
import org.dynamisengine.meshforge.pack.packer.RuntimePackWorkspace;
import org.dynamisengine.meshforge.pack.packer.MeshPacker.RuntimePackWorkspacePool;
import org.dynamisengine.meshforge.pack.simd.SimdNormalPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
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

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 400, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 700, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"--enable-preview", "--add-modules", "jdk.incubator.vector"})
public class VectrixHotPathBaselineBenchmark {
    private static final int BATCH_SIZE = 64;

    @State(Scope.Benchmark)
    public abstract static class BaseMeshState {
        @Param({"SMALL", "MEDIUM", "LARGE", "ATTRIBUTE_HEAVY"})
        String workload;

        @Param({"true", "false"})
        boolean indexed;

        MeshData template;

        @Setup(Level.Trial)
        public void trialSetup() {
            template = buildWorkload(workload);
            if (!indexed) {
                template.setIndices(null);
                template.setSubmeshes(List.of());
            }
        }

        private static MeshData buildWorkload(String workload) {
            return switch (workload) {
                case "SMALL" -> BenchmarkFixtures.createRichGrid(32, 32);
                case "MEDIUM" -> BenchmarkFixtures.createRichGrid(128, 128);
                case "LARGE" -> BenchmarkFixtures.createRichGrid(320, 320);
                case "ATTRIBUTE_HEAVY" -> BenchmarkFixtures.createRichGrid(256, 256);
                default -> throw new IllegalArgumentException("Unknown workload: " + workload);
            };
        }
    }

    @State(Scope.Benchmark)
    public static class PackState extends BaseMeshState {
        RuntimePackWorkspace packWorkspace;
        RuntimePackWorkspacePool packWorkspacePool;
        RuntimePackPlan runtimePlan;

        @Setup(Level.Trial)
        public void setupPackTrial() {
            packWorkspace = new RuntimePackWorkspace();
            packWorkspacePool = new RuntimePackWorkspacePool(8);
            runtimePlan = RuntimeMeshPacker.buildRuntimePlan(template, PackSpec.realtime());
        }
    }

    @State(Scope.Benchmark)
    public static class TangentState extends BaseMeshState {
        MeshData working;
        RecalculateTangentsOp tangentOp;
        RecalculateTangentsOp.Workspace tangentWorkspace;
        MeshContext tangentContext;

        @Setup(Level.Trial)
        public void setupTangentTrial() {
            tangentOp = new RecalculateTangentsOp();
            tangentWorkspace = new RecalculateTangentsOp.Workspace();
            tangentContext = new MeshContext();
        }

        @Setup(Level.Invocation)
        public void setupTangentInvocation() {
            working = BenchmarkFixtures.copyOf(template);
        }
    }

    @State(Scope.Benchmark)
    public static class SimdState {
        @Param({"SMALL", "MEDIUM", "LARGE", "ATTRIBUTE_HEAVY"})
        String workload;

        float[] normals;
        int[] packed;
        int vertexCount;

        @Setup(Level.Trial)
        public void setup() {
            vertexCount = switch (workload) {
                case "SMALL" -> 4_096;
                case "MEDIUM" -> 65_536;
                case "LARGE" -> 300_000;
                case "ATTRIBUTE_HEAVY" -> 180_000;
                default -> throw new IllegalArgumentException("Unknown workload: " + workload);
            };
            normals = new float[vertexCount * 3];
            packed = new int[vertexCount];
            Random random = new Random(42);
            for (int i = 0; i < normals.length; i++) {
                normals[i] = (random.nextFloat() * 2.0f) - 1.0f;
            }
        }
    }

    @Benchmark
    public void meshPackerRealtime(PackState state, Blackhole bh) {
        PackedMesh packed = MeshPacker.pack(state.template, PackSpec.realtime());
        bh.consume(packed.vertexBuffer().capacity());
    }

    @Benchmark
    public void recalculateTangents(TangentState state, Blackhole bh) {
        MeshData out = MeshPipeline.run(state.working, Ops.tangents());
        bh.consume(out.attributeFormats().size());
    }

    @Benchmark
    public void meshPackerRealtimeRuntime(PackState state, Blackhole bh) {
        RuntimeMeshPacker.packInto(state.template, PackSpec.realtime(), state.packWorkspace);
        bh.consume(state.packWorkspace.vertexBytes());
        bh.consume(state.packWorkspace.indexBytes());
    }

    @Benchmark
    public void meshPackerRealtimeRuntimeVertexOnly(PackState state, Blackhole bh) {
        RuntimeMeshPacker.packVertexPayloadInto(state.template, PackSpec.realtime(), state.packWorkspace);
        bh.consume(state.packWorkspace.vertexBytes());
    }

    @Benchmark
    public void meshPackerRealtimeRuntimePooled(PackState state, Blackhole bh) {
        RuntimePackWorkspace workspace = state.packWorkspacePool.acquire();
        try {
            RuntimeMeshPacker.packInto(state.template, PackSpec.realtime(), workspace);
            bh.consume(workspace.vertexBytes());
            bh.consume(workspace.indexBytes());
        } finally {
            state.packWorkspacePool.release(workspace);
        }
    }

    @Benchmark
    public void meshPackerRealtimeRuntimePlanned(PackState state, Blackhole bh) {
        RuntimeMeshPacker.packPlannedInto(state.runtimePlan, state.packWorkspace);
        bh.consume(state.packWorkspace.vertexBytes());
        bh.consume(state.packWorkspace.indexBytes());
    }

    @Benchmark
    public void recalculateTangentsRuntime(TangentState state, Blackhole bh) {
        MeshData out = state.tangentOp.applyWithWorkspace(state.working, state.tangentContext, state.tangentWorkspace);
        bh.consume(out.attributeFormats().size());
    }

    @Benchmark
    public void simdPackOcta(SimdState state, Blackhole bh) {
        SimdNormalPacker.packOctaNormals(state.normals, state.vertexCount, state.packed);
        bh.consume(state.packed[0]);
    }

    @Benchmark
    public void simdPackSnorm8(SimdState state, Blackhole bh) {
        SimdNormalPacker.packSnorm8Normals(state.normals, state.vertexCount, state.packed);
        bh.consume(state.packed[0]);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void meshPackerRealtimeBatch(PackState state, Blackhole bh) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            PackedMesh packed = MeshPacker.pack(state.template, PackSpec.realtime());
            bh.consume(packed.vertexBuffer().capacity());
        }
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void recalculateTangentsBatch(TangentState state, Blackhole bh) {
        MeshData local = state.working;
        for (int i = 0; i < BATCH_SIZE; i++) {
            local = MeshPipeline.run(local, Ops.tangents());
            bh.consume(local.vertexCount());
        }
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void meshPackerRealtimeRuntimeBatch(PackState state, Blackhole bh) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            RuntimeMeshPacker.packInto(state.template, PackSpec.realtime(), state.packWorkspace);
            bh.consume(state.packWorkspace.vertexBytes());
        }
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void meshPackerRealtimeRuntimeVertexOnlyBatch(PackState state, Blackhole bh) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            RuntimeMeshPacker.packVertexPayloadInto(state.template, PackSpec.realtime(), state.packWorkspace);
            bh.consume(state.packWorkspace.vertexBytes());
        }
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void meshPackerRealtimeRuntimePooledBatch(PackState state, Blackhole bh) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            RuntimePackWorkspace workspace = state.packWorkspacePool.acquire();
            try {
                RuntimeMeshPacker.packInto(state.template, PackSpec.realtime(), workspace);
                bh.consume(workspace.vertexBytes());
            } finally {
                state.packWorkspacePool.release(workspace);
            }
        }
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void meshPackerRealtimeRuntimePlannedBatch(PackState state, Blackhole bh) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            RuntimeMeshPacker.packPlannedInto(state.runtimePlan, state.packWorkspace);
            bh.consume(state.packWorkspace.vertexBytes());
        }
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void recalculateTangentsRuntimeBatch(TangentState state, Blackhole bh) {
        MeshData local = state.working;
        for (int i = 0; i < BATCH_SIZE; i++) {
            local = state.tangentOp.applyWithWorkspace(local, state.tangentContext, state.tangentWorkspace);
            bh.consume(local.vertexCount());
        }
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void simdPackOctaBatch(SimdState state, Blackhole bh) {
        for (int i = 0; i < BATCH_SIZE; i++) {
            SimdNormalPacker.packOctaNormals(state.normals, state.vertexCount, state.packed);
            bh.consume(state.packed[i % state.vertexCount]);
        }
    }
}
