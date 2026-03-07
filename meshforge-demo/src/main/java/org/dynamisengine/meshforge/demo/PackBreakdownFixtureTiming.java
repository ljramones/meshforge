package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.packer.PackProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Isolates pack-phase sub-step timings over baseline fixtures.
 */
public final class PackBreakdownFixtureTiming {
    private static final int WARMUP_RUNS = 3;
    private static final int TIMED_RUNS = 7;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private PackBreakdownFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        boolean fastLoader = true;
        boolean packMinimal = false;
        String fixtureFilter = null;
        for (String arg : args) {
            if ("--legacy".equals(arg)) {
                fastLoader = false;
            } else if ("--fast".equals(arg)) {
                fastLoader = true;
            } else if ("--pack-minimal".equals(arg)) {
                packMinimal = true;
            } else if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            }
        }

        Path fixturesDir = Path.of("fixtures", "baseline");
        if (!Files.isDirectory(fixturesDir)) {
            System.out.println("Missing baseline fixtures directory: " + fixturesDir.toAbsolutePath());
            return;
        }

        final String fixtureFilterValue = fixtureFilter;
        List<Path> fixtures = Files.list(fixturesDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obj"))
            .filter(p -> fixtureFilterValue == null || p.getFileName().toString().toLowerCase(Locale.ROOT).contains(fixtureFilterValue))
            .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
            .toList();

        if (fixtures.isEmpty()) {
            System.out.println("No OBJ fixtures found in " + fixturesDir.toAbsolutePath());
            return;
        }

        MeshLoaders loaders = fastLoader ? MeshLoaders.defaultsFast() : MeshLoaders.defaultsLegacy();
        List<Row> rows = new ArrayList<>();

        System.out.println(
            "Pack breakdown (" + (fastLoader ? "fast" : "legacy") +
                " loader, " + (packMinimal ? "minimal" : "realtime") + " pack spec, " +
                "median + p95 over " + TIMED_RUNS +
                " timed runs after " + WARMUP_RUNS + " warmup runs)"
        );
        System.out.println();

        for (Path fixture : fixtures) {
            Row row = profileFixture(loaders, fixture, packMinimal);
            rows.add(row);
            System.out.printf(
                Locale.ROOT,
                "%-20s total: %12s (p95 %12s) | vertex: %12s | pos: %10s | nrm: %10s | tan: %10s | uv: %10s | col: %10s | skin: %10s | index: %10s%n",
                row.fixture() + ":",
                formatUs(row.totalMedianUs()),
                formatUs(row.totalP95Us()),
                formatUs(row.vertexWriteMedianUs()),
                formatUs(row.positionWriteMedianUs()),
                formatUs(row.normalPackMedianUs()),
                formatUs(row.tangentPackMedianUs()),
                formatUs(row.uvPackMedianUs()),
                formatUs(row.colorPackMedianUs()),
                formatUs(row.skinPackMedianUs()),
                formatUs(row.indexPackMedianUs())
            );
        }

        Path outDir = Path.of("perf", "results");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("pack-breakdown-" + (fastLoader ? "fast" : "legacy") + "-" + LocalDateTime.now().format(TS) + ".csv");
        writeCsv(outFile, rows);
        System.out.println();
        System.out.println("Results written to: " + outFile.toAbsolutePath());
    }

    private static Row profileFixture(MeshLoaders loaders, Path fixture, boolean packMinimal) throws IOException {
        MeshData loaded = loaders.load(fixture);
        MeshData processed = Pipelines.realtimeFast(loaded);
        var packSpec = packMinimal ? Packers.realtimeMinimal() : Packers.realtime();

        List<Long> resolveUs = new ArrayList<>(TIMED_RUNS);
        List<Long> layoutUs = new ArrayList<>(TIMED_RUNS);
        List<Long> vertexWriteUs = new ArrayList<>(TIMED_RUNS);
        List<Long> positionWriteUs = new ArrayList<>(TIMED_RUNS);
        List<Long> normalPackUs = new ArrayList<>(TIMED_RUNS);
        List<Long> tangentPackUs = new ArrayList<>(TIMED_RUNS);
        List<Long> uvPackUs = new ArrayList<>(TIMED_RUNS);
        List<Long> colorPackUs = new ArrayList<>(TIMED_RUNS);
        List<Long> skinPackUs = new ArrayList<>(TIMED_RUNS);
        List<Long> indexPackUs = new ArrayList<>(TIMED_RUNS);
        List<Long> submeshCopyUs = new ArrayList<>(TIMED_RUNS);
        List<Long> totalUs = new ArrayList<>(TIMED_RUNS);

        int strideBytes = 0;
        int vertexCount = 0;
        int triangleCount = 0;

        for (int i = 0; i < WARMUP_RUNS + TIMED_RUNS; i++) {
            PackProfile profile = new PackProfile();
            MeshPacker.pack(processed, packSpec, profile);

            if (i >= WARMUP_RUNS) {
                resolveUs.add(profile.resolveAttributesNs() / 1_000L);
                layoutUs.add(profile.layoutNs() / 1_000L);
                vertexWriteUs.add(profile.vertexWriteNs() / 1_000L);
                positionWriteUs.add(profile.positionWriteNs() / 1_000L);
                normalPackUs.add(profile.normalPackNs() / 1_000L);
                tangentPackUs.add(profile.tangentPackNs() / 1_000L);
                uvPackUs.add(profile.uvPackNs() / 1_000L);
                colorPackUs.add(profile.colorPackNs() / 1_000L);
                skinPackUs.add(profile.skinPackNs() / 1_000L);
                indexPackUs.add(profile.indexPackNs() / 1_000L);
                submeshCopyUs.add(profile.submeshCopyNs() / 1_000L);
                totalUs.add(profile.totalNs() / 1_000L);
            }

            strideBytes = profile.strideBytes();
            vertexCount = profile.vertexCount();
            triangleCount = profile.indexCount() / 3;
        }

        double totalMs = median(totalUs) / 1_000.0;
        double perMillionVertices = vertexCount == 0 ? 0.0 : totalMs / (vertexCount / 1_000_000.0);
        double perMillionTriangles = triangleCount == 0 ? 0.0 : totalMs / (triangleCount / 1_000_000.0);

        return new Row(
            fixture.getFileName().toString(),
            vertexCount,
            triangleCount,
            strideBytes,
            median(resolveUs), p95(resolveUs),
            median(layoutUs), p95(layoutUs),
            median(vertexWriteUs), p95(vertexWriteUs),
            median(positionWriteUs), p95(positionWriteUs),
            median(normalPackUs), p95(normalPackUs),
            median(tangentPackUs), p95(tangentPackUs),
            median(uvPackUs), p95(uvPackUs),
            median(colorPackUs), p95(colorPackUs),
            median(skinPackUs), p95(skinPackUs),
            median(indexPackUs), p95(indexPackUs),
            median(submeshCopyUs), p95(submeshCopyUs),
            median(totalUs), p95(totalUs),
            perMillionVertices,
            perMillionTriangles
        );
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
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private static void writeCsv(Path outFile, List<Row> rows) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("fixture,vertices,triangles,stride_bytes,")
            .append("resolve_median_us,resolve_p95_us,")
            .append("layout_median_us,layout_p95_us,")
            .append("vertex_write_median_us,vertex_write_p95_us,")
            .append("position_write_median_us,position_write_p95_us,")
            .append("normal_pack_median_us,normal_pack_p95_us,")
            .append("tangent_pack_median_us,tangent_pack_p95_us,")
            .append("uv_pack_median_us,uv_pack_p95_us,")
            .append("color_pack_median_us,color_pack_p95_us,")
            .append("skin_pack_median_us,skin_pack_p95_us,")
            .append("index_pack_median_us,index_pack_p95_us,")
            .append("submesh_copy_median_us,submesh_copy_p95_us,")
            .append("total_median_us,total_p95_us,")
            .append("total_ms_per_1m_vertices,total_ms_per_1m_triangles\n");
        for (Row row : rows) {
            sb.append(row.fixture()).append(',')
                .append(row.vertices()).append(',')
                .append(row.triangles()).append(',')
                .append(row.strideBytes()).append(',')
                .append(row.resolveMedianUs()).append(',')
                .append(row.resolveP95Us()).append(',')
                .append(row.layoutMedianUs()).append(',')
                .append(row.layoutP95Us()).append(',')
                .append(row.vertexWriteMedianUs()).append(',')
                .append(row.vertexWriteP95Us()).append(',')
                .append(row.positionWriteMedianUs()).append(',')
                .append(row.positionWriteP95Us()).append(',')
                .append(row.normalPackMedianUs()).append(',')
                .append(row.normalPackP95Us()).append(',')
                .append(row.tangentPackMedianUs()).append(',')
                .append(row.tangentPackP95Us()).append(',')
                .append(row.uvPackMedianUs()).append(',')
                .append(row.uvPackP95Us()).append(',')
                .append(row.colorPackMedianUs()).append(',')
                .append(row.colorPackP95Us()).append(',')
                .append(row.skinPackMedianUs()).append(',')
                .append(row.skinPackP95Us()).append(',')
                .append(row.indexPackMedianUs()).append(',')
                .append(row.indexPackP95Us()).append(',')
                .append(row.submeshCopyMedianUs()).append(',')
                .append(row.submeshCopyP95Us()).append(',')
                .append(row.totalMedianUs()).append(',')
                .append(row.totalP95Us()).append(',')
                .append(String.format(Locale.ROOT, "%.3f", row.totalMsPerMillionVertices())).append(',')
                .append(String.format(Locale.ROOT, "%.3f", row.totalMsPerMillionTriangles())).append('\n');
        }
        Files.writeString(outFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String formatUs(long us) {
        if (us >= 1_000L) {
            return String.format(Locale.ROOT, "%.3f ms", us / 1_000.0);
        }
        return us + " us";
    }

    private record Row(
        String fixture,
        int vertices,
        int triangles,
        int strideBytes,
        long resolveMedianUs,
        long resolveP95Us,
        long layoutMedianUs,
        long layoutP95Us,
        long vertexWriteMedianUs,
        long vertexWriteP95Us,
        long positionWriteMedianUs,
        long positionWriteP95Us,
        long normalPackMedianUs,
        long normalPackP95Us,
        long tangentPackMedianUs,
        long tangentPackP95Us,
        long uvPackMedianUs,
        long uvPackP95Us,
        long colorPackMedianUs,
        long colorPackP95Us,
        long skinPackMedianUs,
        long skinPackP95Us,
        long indexPackMedianUs,
        long indexPackP95Us,
        long submeshCopyMedianUs,
        long submeshCopyP95Us,
        long totalMedianUs,
        long totalP95Us,
        double totalMsPerMillionVertices,
        double totalMsPerMillionTriangles
    ) {
    }
}
