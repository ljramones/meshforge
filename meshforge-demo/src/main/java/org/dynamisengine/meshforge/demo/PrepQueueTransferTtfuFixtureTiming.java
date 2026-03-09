package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.gpu.GpuGeometryUploadPlan;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.mgi.MgiMeshDataCodec;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures runtime geometry timing split with explicit four-point timestamps:
 * T0 prep start, T1 packed payload ready, T2 submit accepted, T3 transfer complete.
 */
public final class PrepQueueTransferTtfuFixtureTiming {
    private static final int DEFAULT_WARMUP = 2;
    private static final int DEFAULT_RUNS = 9;
    private static final int DEFAULT_INFLIGHT = 2;

    private PrepQueueTransferTtfuFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureFilter = null;
        int warmup = DEFAULT_WARMUP;
        int runs = DEFAULT_RUNS;
        int maxInflight = DEFAULT_INFLIGHT;
        Mode mode = Mode.BOTH;

        for (String arg : args) {
            if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            } else if (arg.startsWith("--warmup=")) {
                warmup = parsePositive(arg.substring("--warmup=".length()), "warmup");
            } else if (arg.startsWith("--runs=")) {
                runs = parsePositive(arg.substring("--runs=".length()), "runs");
            } else if (arg.startsWith("--max-inflight=")) {
                maxInflight = parsePositive(arg.substring("--max-inflight=".length()), "max-inflight");
            } else if (arg.startsWith("--mode=")) {
                mode = parseMode(arg.substring("--mode=".length()));
            }
        }

        Path fixtureDir = Path.of("fixtures", "baseline");
        if (!Files.isDirectory(fixtureDir)) {
            System.out.println("Missing fixture directory: " + fixtureDir.toAbsolutePath());
            return;
        }

        final String filter = fixtureFilter;
        List<Path> fixtures = Files.list(fixtureDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obj"))
            .filter(p -> filter == null || p.getFileName().toString().toLowerCase(Locale.ROOT).contains(filter))
            .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
            .toList();

        if (fixtures.isEmpty()) {
            System.out.println("No fixtures matched.");
            return;
        }

        MeshLoaders loaders = MeshLoaders.defaultsFast();
        PackSpec spec = Packers.realtime();

        List<Row> rows = new ArrayList<>();
        for (Path fixture : fixtures) {
            if (mode.includesFull()) {
                rows.add(measureFixture(loaders, spec, fixture, warmup, runs, maxInflight, Mode.FULL));
            }
            if (mode.includesMgiFull()) {
                rows.add(measureFixture(loaders, spec, fixture, warmup, runs, maxInflight, Mode.MGI_FULL));
            }
            if (mode.includesRuntimeOnly()) {
                rows.add(measureFixture(loaders, spec, fixture, warmup, runs, maxInflight, Mode.RUNTIME_ONLY));
            }
        }

        System.out.println("prep+queue+transfer timing (median + p95)");
        System.out.printf(Locale.ROOT, "warmup=%d runs=%d maxInflight=%d mode=%s%n", warmup, runs, maxInflight, mode.label);
        System.out.println();
        System.out.println("| Fixture | Mode | Load/Clone ms | Pipeline ms | Pipeline Attr ms | Pipeline Topology ms | Plan ms | Pack ms | Pack Vertex ms | Pack Index ms | Pack Submesh ms | Bridge ms | Queue ms | Transfer ms | Total TTFU ms | Triangles | Upload Bytes |");
        System.out.println("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Row row : rows) {
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %s | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %d | %d |%n",
                row.name,
                row.mode,
                row.loadOrCloneMedianMs,
                row.pipelineMedianMs,
                row.pipelineAttrMedianMs,
                row.pipelineTopologyMedianMs,
                row.planMedianMs,
                row.packMedianMs,
                row.packVertexPayloadMedianMs,
                row.packIndexPayloadMedianMs,
                row.packSubmeshMedianMs,
                row.bridgeMedianMs,
                row.queueMedianMs,
                row.transferMedianMs,
                row.totalMedianMs,
                row.triangles,
                row.uploadBytes
            );
        }

        System.out.println();
        System.out.println("p95 breakdown");
        System.out.println("| Fixture | Mode | Load/Clone p95 | Pipeline p95 | Pipeline Attr p95 | Pipeline Topology p95 | Plan p95 | Pack p95 | Pack Vertex p95 | Pack Index p95 | Pack Submesh p95 | Bridge p95 | Queue p95 | Transfer p95 | Total TTFU p95 |");
        System.out.println("|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Row row : rows) {
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %s | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f |%n",
                row.name,
                row.mode,
                row.loadOrCloneP95Ms,
                row.pipelineP95Ms,
                row.pipelineAttrP95Ms,
                row.pipelineTopologyP95Ms,
                row.planP95Ms,
                row.packP95Ms,
                row.packVertexPayloadP95Ms,
                row.packIndexPayloadP95Ms,
                row.packSubmeshP95Ms,
                row.bridgeP95Ms,
                row.queueP95Ms,
                row.transferP95Ms,
                row.totalP95Ms
            );
        }
    }

    private static Row measureFixture(
        MeshLoaders loaders,
        PackSpec spec,
        Path fixture,
        int warmup,
        int runs,
        int maxInflight,
        Mode mode
    ) throws Exception {
        int total = warmup + runs;
        List<Long> loadOrCloneNs = new ArrayList<>(runs);
        List<Long> pipelineNs = new ArrayList<>(runs);
        List<Long> pipelineAttrNs = new ArrayList<>(runs);
        List<Long> pipelineTopologyNs = new ArrayList<>(runs);
        List<Long> planNs = new ArrayList<>(runs);
        List<Long> packNs = new ArrayList<>(runs);
        List<Long> packVertexPayloadNs = new ArrayList<>(runs);
        List<Long> packIndexPayloadNs = new ArrayList<>(runs);
        List<Long> packSubmeshNs = new ArrayList<>(runs);
        List<Long> bridgeNs = new ArrayList<>(runs);
        List<Long> queueNs = new ArrayList<>(runs);
        List<Long> transferNs = new ArrayList<>(runs);
        List<Long> totalNs = new ArrayList<>(runs);

        int triangles = 0;
        int uploadBytes = 0;

        MeshData baseline = null;
        Path mgiFixture = null;
        MgiMeshDataCodec mgiCodec = null;
        if (mode == Mode.RUNTIME_ONLY) {
            baseline = loaders.load(fixture);
        } else if (mode == Mode.MGI_FULL) {
            mgiCodec = new MgiMeshDataCodec();
            mgiFixture = ensureMgiFixture(loaders, mgiCodec, fixture);
        }

        try (AsyncUploadSimulator uploader = new AsyncUploadSimulator(maxInflight)) {
            for (int i = 0; i < total; i++) {
                long t0 = System.nanoTime();

                MeshData loaded;
                if (mode == Mode.FULL) {
                    loaded = loaders.load(fixture);
                } else if (mode == Mode.MGI_FULL) {
                    loaded = mgiCodec.read(Files.readAllBytes(mgiFixture));
                } else {
                    loaded = copyOf(baseline);
                }
                long tLoadOrClone = System.nanoTime();

                Pipelines.RuntimeStageProfile pipelineProfile = new Pipelines.RuntimeStageProfile();
                MeshData processed = Pipelines.realtimeFastProfiled(loaded, pipelineProfile);
                long tPipeline = System.nanoTime();

                MeshPacker.RuntimePackPlan runtimePlan = MeshPacker.buildRuntimePlan(processed, spec);
                long tPlan = System.nanoTime();

                MeshPacker.RuntimePackWorkspace workspace = new MeshPacker.RuntimePackWorkspace();
                MeshPacker.RuntimePackProfile packProfile = new MeshPacker.RuntimePackProfile();
                MeshPacker.packPlannedIntoProfiled(runtimePlan, workspace, packProfile);
                long tPack = System.nanoTime();

                RuntimeGeometryPayload payload =
                    MeshForgeGpuBridge.payloadFromRuntimeWorkspace(runtimePlan.layout(), workspace);
                GpuGeometryUploadPlan uploadPlan = MeshForgeGpuBridge.buildUploadPlan(payload);
                long t1 = System.nanoTime();

                PendingTransfer pending = uploader.submit(payload, uploadPlan, t0, t1);
                TransferTiming timing = pending.await();

                if (i >= warmup) {
                    loadOrCloneNs.add(tLoadOrClone - t0);
                    pipelineNs.add(tPipeline - tLoadOrClone);
                    pipelineAttrNs.add(pipelineProfile.normalsNs() + pipelineProfile.tangentsNs());
                    pipelineTopologyNs.add(
                        pipelineProfile.validateNs()
                            + pipelineProfile.removeDegeneratesNs()
                            + pipelineProfile.boundsNs());
                    planNs.add(tPlan - tPipeline);
                    packNs.add(tPack - tPlan);
                    packVertexPayloadNs.add(packProfile.vertexPayloadNs());
                    packIndexPayloadNs.add(packProfile.indexPayloadNs());
                    packSubmeshNs.add(packProfile.submeshMetadataNs());
                    bridgeNs.add(t1 - tPack);
                    queueNs.add(timing.queueWaitNanos());
                    transferNs.add(timing.transferNanos());
                    totalNs.add(timing.totalTtfuNanos());
                }

                if (triangles == 0) {
                    triangles = payload.indexCount() / 3;
                }
                if (uploadBytes == 0) {
                    uploadBytes = uploadPlan.vertexBinding().byteSize()
                        + (uploadPlan.indexBinding() == null ? 0 : uploadPlan.indexBinding().byteSize());
                }
            }
        }

        return new Row(
            fixture.getFileName().toString(),
            mode.label,
            toMs(median(loadOrCloneNs)),
            toMs(p95(loadOrCloneNs)),
            toMs(median(pipelineNs)),
            toMs(p95(pipelineNs)),
            toMs(median(pipelineAttrNs)),
            toMs(p95(pipelineAttrNs)),
            toMs(median(pipelineTopologyNs)),
            toMs(p95(pipelineTopologyNs)),
            toMs(median(planNs)),
            toMs(p95(planNs)),
            toMs(median(packNs)),
            toMs(p95(packNs)),
            toMs(median(packVertexPayloadNs)),
            toMs(p95(packVertexPayloadNs)),
            toMs(median(packIndexPayloadNs)),
            toMs(p95(packIndexPayloadNs)),
            toMs(median(packSubmeshNs)),
            toMs(p95(packSubmeshNs)),
            toMs(median(bridgeNs)),
            toMs(p95(bridgeNs)),
            toMs(median(queueNs)),
            toMs(p95(queueNs)),
            toMs(median(transferNs)),
            toMs(p95(transferNs)),
            toMs(median(totalNs)),
            toMs(p95(totalNs)),
            triangles,
            uploadBytes
        );
    }

    private static MeshData copyOf(MeshData src) {
        int[] indices = src.indicesOrNull();
        List<Submesh> copiedSubmeshes = new ArrayList<>(src.submeshes());
        MeshData dst = new MeshData(
            src.topology(),
            src.schema(),
            src.vertexCount(),
            indices == null ? null : indices.clone(),
            copiedSubmeshes
        );

        for (Map.Entry<AttributeKey, VertexFormat> entry : src.attributeFormats().entrySet()) {
            AttributeKey key = entry.getKey();
            VertexFormat format = entry.getValue();
            VertexAttributeView in = src.attribute(key.semantic(), key.setIndex());
            VertexAttributeView out = dst.attribute(key.semantic(), key.setIndex());
            copyAttribute(in, out, format);
        }

        dst.setBounds(src.boundsOrNull());
        dst.setMorphTargets(src.morphTargets());
        return dst;
    }

    private static void copyAttribute(VertexAttributeView in, VertexAttributeView out, VertexFormat format) {
        int vc = in.vertexCount();
        int comps = format.components();
        switch (format.kind()) {
            case FLOAT -> {
                for (int i = 0; i < vc; i++) {
                    for (int c = 0; c < comps; c++) {
                        out.setFloat(i, c, in.getFloat(i, c));
                    }
                }
            }
            case INT, SHORT, BYTE -> {
                for (int i = 0; i < vc; i++) {
                    for (int c = 0; c < comps; c++) {
                        out.setInt(i, c, in.getInt(i, c));
                    }
                }
            }
        }
    }

    private static int simulateTransfer(RuntimeGeometryPayload payload, GpuGeometryUploadPlan plan) {
        ByteBuffer vertexSrc = payload.vertexBytes().asReadOnlyBuffer();
        vertexSrc.position(0);
        ByteBuffer vertexDst = ByteBuffer.allocateDirect(plan.vertexBinding().byteSize());
        vertexDst.put(vertexSrc);

        int checksum = vertexDst.capacity();
        if (plan.indexBinding() != null && payload.indexBytes() != null) {
            ByteBuffer indexSrc = payload.indexBytes().asReadOnlyBuffer();
            indexSrc.position(0);
            ByteBuffer indexDst = ByteBuffer.allocateDirect(plan.indexBinding().byteSize());
            indexDst.put(indexSrc);
            checksum += indexDst.capacity();
        }
        return checksum;
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private static long p95(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int idx = (int) Math.ceil(sorted.size() * 0.95) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    private static double toMs(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static int parsePositive(String raw, String label) {
        int parsed = Integer.parseInt(raw);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be > 0");
        }
        return parsed;
    }

    private static Mode parseMode(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "full" -> Mode.FULL;
            case "mgi-full" -> Mode.MGI_FULL;
            case "runtime-only" -> Mode.RUNTIME_ONLY;
            case "both" -> Mode.BOTH;
            case "all" -> Mode.ALL;
            default -> throw new IllegalArgumentException("--mode must be full|mgi-full|runtime-only|both|all");
        };
    }

    private static Path ensureMgiFixture(MeshLoaders loaders, MgiMeshDataCodec codec, Path sourceObj) throws Exception {
        Path mgi = mgiPathFor(sourceObj);
        if (Files.isRegularFile(mgi)) {
            return mgi;
        }

        MeshData loaded = loaders.load(sourceObj);
        byte[] bytes = codec.write(loaded);
        Files.write(mgi, bytes);
        return mgi;
    }

    private static Path mgiPathFor(Path sourceObj) {
        String file = sourceObj.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String base = dot <= 0 ? file : file.substring(0, dot);
        return sourceObj.resolveSibling(base + ".mgi");
    }

    private record PendingTransfer(long t0, long t1, long t2, CompletableFuture<Integer> transferFuture) {
        private TransferTiming await() throws Exception {
            transferFuture.get();
            long t3 = System.nanoTime();
            return new TransferTiming(t2 - t1, t3 - t2, t3 - t0);
        }
    }

    private record TransferTiming(long queueWaitNanos, long transferNanos, long totalTtfuNanos) {
    }

    private record Row(
        String name,
        String mode,
        double loadOrCloneMedianMs,
        double loadOrCloneP95Ms,
        double pipelineMedianMs,
        double pipelineP95Ms,
        double pipelineAttrMedianMs,
        double pipelineAttrP95Ms,
        double pipelineTopologyMedianMs,
        double pipelineTopologyP95Ms,
        double planMedianMs,
        double planP95Ms,
        double packMedianMs,
        double packP95Ms,
        double packVertexPayloadMedianMs,
        double packVertexPayloadP95Ms,
        double packIndexPayloadMedianMs,
        double packIndexPayloadP95Ms,
        double packSubmeshMedianMs,
        double packSubmeshP95Ms,
        double bridgeMedianMs,
        double bridgeP95Ms,
        double queueMedianMs,
        double queueP95Ms,
        double transferMedianMs,
        double transferP95Ms,
        double totalMedianMs,
        double totalP95Ms,
        int triangles,
        int uploadBytes
    ) {
    }

    private enum Mode {
        FULL("full"),
        MGI_FULL("mgi-full"),
        RUNTIME_ONLY("runtime-only"),
        BOTH("both"),
        ALL("all");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        private boolean includesFull() {
            return this == FULL || this == BOTH || this == ALL;
        }

        private boolean includesMgiFull() {
            return this == MGI_FULL || this == ALL;
        }

        private boolean includesRuntimeOnly() {
            return this == RUNTIME_ONLY || this == BOTH || this == ALL;
        }
    }

    private static final class AsyncUploadSimulator implements AutoCloseable {
        private final Semaphore inflight;
        private final ExecutorService workers;

        private AsyncUploadSimulator(int maxInflight) {
            this.inflight = new Semaphore(maxInflight, true);
            this.workers = Executors.newFixedThreadPool(maxInflight, new UploadThreadFactory());
        }

        private PendingTransfer submit(
            RuntimeGeometryPayload payload,
            GpuGeometryUploadPlan plan,
            long t0,
            long t1
        ) throws InterruptedException {
            inflight.acquire();
            long t2 = System.nanoTime();
            CompletableFuture<Integer> future =
                CompletableFuture.supplyAsync(() -> simulateTransfer(payload, plan), workers)
                    .whenComplete((ignored, error) -> inflight.release());
            return new PendingTransfer(t0, t1, t2, future);
        }

        @Override
        public void close() {
            workers.shutdownNow();
        }
    }

    private static final class UploadThreadFactory implements ThreadFactory {
        private final AtomicInteger nextId = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread t = new Thread(runnable, "meshforge-upload-sim-" + nextId.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}
