package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
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

/**
 * Measures lower-bound payload copy cost for trusted-preprocessed mesh geometry.
 * This intentionally excludes topology cleanup and attribute generation costs.
 */
public final class PayloadCopyFloorFixtureTiming {
    private static final int DEFAULT_WARMUP = 2;
    private static final int DEFAULT_RUNS = 15;

    private PayloadCopyFloorFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureFilter = "revit";
        int warmup = DEFAULT_WARMUP;
        int runs = DEFAULT_RUNS;

        for (String arg : args) {
            if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            } else if (arg.startsWith("--warmup=")) {
                warmup = parsePositive(arg.substring("--warmup=".length()), "warmup");
            } else if (arg.startsWith("--runs=")) {
                runs = parsePositive(arg.substring("--runs=".length()), "runs");
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
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        PackSpec spec = Packers.realtime();

        List<Row> rows = new ArrayList<>();
        for (Path fixture : fixtures) {
            rows.add(measureFixture(loaders, codec, spec, fixture, warmup, runs));
        }

        System.out.println("payload copy floor timing (trusted-preprocessed payload, median + p95)");
        System.out.printf(Locale.ROOT, "warmup=%d runs=%d%n", warmup, runs);
        System.out.println();
        System.out.println("| Fixture | Vertex Bytes | Index Bytes | Total Bytes | Vertex Copy ms | Index Copy ms | Total Copy+Setup ms | Total p95 ms | Effective GB/s | Vertex GB/s | Index GB/s |") ;
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Row row : rows) {
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %d | %d | %d | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f |%n",
                row.fixture,
                row.vertexBytes,
                row.indexBytes,
                row.totalBytes,
                row.vertexMedianMs,
                row.indexMedianMs,
                row.totalMedianMs,
                row.totalP95Ms,
                row.totalGbps,
                row.vertexGbps,
                row.indexGbps
            );
        }
    }

    private static Row measureFixture(
        MeshLoaders loaders,
        MgiMeshDataCodec codec,
        PackSpec spec,
        Path fixture,
        int warmup,
        int runs
    ) throws Exception {
        Path trustedSidecar = ensureTrustedMgiFixture(loaders, codec, fixture);
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(Files.readAllBytes(trustedSidecar));

        MeshData mesh = decoded.meshData();
        MeshPacker.RuntimePackPlan plan = MeshPacker.buildRuntimePlan(mesh, spec);
        MeshPacker.RuntimePackWorkspace workspace = new MeshPacker.RuntimePackWorkspace();
        MeshPacker.packPlannedInto(plan, workspace);

        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromRuntimeWorkspace(plan.layout(), workspace);
        GpuGeometryUploadPlan uploadPlan = MeshForgeGpuBridge.buildUploadPlan(payload);

        ByteBuffer vertexSrcTemplate = payload.vertexBytes().asReadOnlyBuffer();
        vertexSrcTemplate.position(0);
        ByteBuffer indexSrcTemplate = payload.indexBytes() == null ? null : payload.indexBytes().asReadOnlyBuffer();
        if (indexSrcTemplate != null) {
            indexSrcTemplate.position(0);
        }

        int vertexBytes = uploadPlan.vertexBinding().byteSize();
        int indexBytes = uploadPlan.indexBinding() == null ? 0 : uploadPlan.indexBinding().byteSize();
        int totalBytes = vertexBytes + indexBytes;

        ByteBuffer vertexDst = ByteBuffer.allocateDirect(vertexBytes);
        ByteBuffer indexDst = indexBytes == 0 ? null : ByteBuffer.allocateDirect(indexBytes);

        int total = warmup + runs;
        List<Long> vertexNs = new ArrayList<>(runs);
        List<Long> indexNs = new ArrayList<>(runs);
        List<Long> totalNs = new ArrayList<>(runs);

        for (int i = 0; i < total; i++) {
            long t0 = System.nanoTime();
            long tVertexStart = t0;
            copyBuffer(vertexSrcTemplate, vertexDst);
            long tVertexEnd = System.nanoTime();

            long tIndexStart = tVertexEnd;
            if (indexSrcTemplate != null && indexDst != null) {
                copyBuffer(indexSrcTemplate, indexDst);
            }
            long tIndexEnd = System.nanoTime();

            if (i >= warmup) {
                vertexNs.add(tVertexEnd - tVertexStart);
                indexNs.add(tIndexEnd - tIndexStart);
                totalNs.add(tIndexEnd - t0);
            }
        }

        double vertexMedianMs = toMs(median(vertexNs));
        double indexMedianMs = toMs(median(indexNs));
        double totalMedianMs = toMs(median(totalNs));
        double totalP95Ms = toMs(p95(totalNs));

        return new Row(
            fixture.getFileName().toString(),
            vertexBytes,
            indexBytes,
            totalBytes,
            vertexMedianMs,
            indexMedianMs,
            totalMedianMs,
            totalP95Ms,
            toGbps(totalBytes, totalMedianMs),
            toGbps(vertexBytes, vertexMedianMs),
            indexBytes == 0 ? 0.0 : toGbps(indexBytes, indexMedianMs)
        );
    }

    private static Path ensureTrustedMgiFixture(MeshLoaders loaders, MgiMeshDataCodec codec, Path sourceObj) throws Exception {
        Path trusted = trustedMgiPathFor(sourceObj);
        if (Files.isRegularFile(trusted)) {
            return trusted;
        }

        MeshData loaded = loaders.load(sourceObj);
        MeshData preprocessed = Pipelines.realtimeFast(loaded);
        byte[] bytes = codec.write(preprocessed);
        Files.write(trusted, bytes);
        return trusted;
    }

    private static Path trustedMgiPathFor(Path sourceObj) {
        String file = sourceObj.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String base = dot <= 0 ? file : file.substring(0, dot);
        return sourceObj.resolveSibling(base + ".trusted.mgi");
    }

    private static void copyBuffer(ByteBuffer srcTemplate, ByteBuffer dst) {
        ByteBuffer src = srcTemplate.duplicate();
        src.position(0);
        dst.clear();
        dst.put(src);
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

    private static double toGbps(long bytes, double millis) {
        if (bytes <= 0 || millis <= 0.0) {
            return 0.0;
        }
        double seconds = millis / 1_000.0;
        return (bytes / seconds) / 1_000_000_000.0;
    }

    private static int parsePositive(String raw, String label) {
        int parsed = Integer.parseInt(raw);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be > 0");
        }
        return parsed;
    }

    private record Row(
        String fixture,
        int vertexBytes,
        int indexBytes,
        int totalBytes,
        double vertexMedianMs,
        double indexMedianMs,
        double totalMedianMs,
        double totalP95Ms,
        double totalGbps,
        double vertexGbps,
        double indexGbps
    ) {
    }
}
