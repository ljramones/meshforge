package org.meshforge.demo;

import org.meshforge.api.Packers;
import org.meshforge.api.Pipelines;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.loader.FastObjMeshLoader;
import org.meshforge.loader.MeshLoaders;
import org.meshforge.pack.packer.MeshPacker;
import org.meshforge.pack.spec.PackSpec;

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
 * Phase-split baseline fixture timing:
 * parse (load) / pipeline / pack / total, reported as median+p95.
 */
public final class PhaseSplitFixtureTiming {
    private static final int DEFAULT_WARMUP_RUNS = 3;
    private static final int DEFAULT_TIMED_RUNS = 7;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private PhaseSplitFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        boolean fastLoader = true;
        boolean profilePack = false;
        boolean profileParse = false;
        boolean packMinimal = false;
        boolean parseOnly = false;
        String fixtureFilter = null;
        int warmupRuns = DEFAULT_WARMUP_RUNS;
        int timedRuns = DEFAULT_TIMED_RUNS;
        for (String arg : args) {
            if ("--legacy".equals(arg)) {
                fastLoader = false;
            } else if ("--fast".equals(arg)) {
                fastLoader = true;
            } else if ("--profile-pack".equals(arg)) {
                profilePack = true;
            } else if ("--profile-parse".equals(arg)) {
                profileParse = true;
            } else if ("--pack-minimal".equals(arg)) {
                packMinimal = true;
            } else if ("--parse-only".equals(arg)) {
                parseOnly = true;
            } else if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            } else if (arg.startsWith("--warmup=")) {
                warmupRuns = parsePositive(arg.substring("--warmup=".length()), "warmup");
            } else if (arg.startsWith("--runs=")) {
                timedRuns = parsePositive(arg.substring("--runs=".length()), "runs");
            }
        }

        if (profilePack) {
            PackBreakdownFixtureTiming.main(args);
            return;
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
        List<TimingStats> all = new ArrayList<>();
        FastObjMeshLoader.setParseProfilingEnabled(fastLoader && profileParse);
        try {
            System.out.println(
                "Phase-split timing (" + (fastLoader ? "fast" : "legacy") +
                    ", " + (packMinimal ? "minimal" : "realtime") + " pack spec" +
                    ", " + (parseOnly ? "parse-only" : "full phases") +
                    ", " + (profileParse ? "parse-profiled" : "parse-unprofiled") +
                    ", median + p95 over " + timedRuns +
                    " timed runs after " + warmupRuns + " warmup runs)"
            );
            System.out.println();
            for (Path fixture : fixtures) {
                TimingStats stats = runFixture(loaders, fixture, packMinimal, parseOnly, fastLoader && profileParse, warmupRuns, timedRuns);
                all.add(stats);
                System.out.printf(
                    Locale.ROOT,
                    "%-20s parse: %12s (p95 %12s) | pipeline: %12s (p95 %12s) | pack: %12s (p95 %12s) | total: %12s (p95 %12s)%n",
                    stats.fixture() + ":",
                    formatUs(stats.parseMedianUs()), formatUs(stats.parseP95Us()),
                    formatUs(stats.pipelineMedianUs()), formatUs(stats.pipelineP95Us()),
                    formatUs(stats.packMedianUs()), formatUs(stats.packP95Us()),
                    formatUs(stats.totalMedianUs()), formatUs(stats.totalP95Us())
                );
                System.out.printf(
                    Locale.ROOT,
                    "  verts=%d tris=%d parse/1M verts=%.3f us",
                    stats.vertexCount(),
                    stats.triangleCount(),
                    stats.parseUsPer1MVerts()
                );
                if (profileParse) {
                    System.out.printf(
                        Locale.ROOT,
                        " | parse breakdown scan=%s float=%s face=%s%n",
                        formatUs(stats.parseScanMedianUs()),
                        formatUs(stats.parseFloatMedianUs()),
                        formatUs(stats.parseFaceMedianUs())
                    );
                } else {
                    System.out.println();
                }
            }

            Path outDir = Path.of("perf", "results");
            Files.createDirectories(outDir);
            String suffix = parseOnly ? "-parse-only" : "";
            Path outFile = outDir.resolve("phase-split-" + (fastLoader ? "fast" : "legacy") + suffix + "-" + LocalDateTime.now().format(TS) + ".csv");
            writeCsv(outFile, all);
            System.out.println();
            System.out.println("Results written to: " + outFile.toAbsolutePath());
        } finally {
            FastObjMeshLoader.setParseProfilingEnabled(false);
        }
    }

    private static TimingStats runFixture(
        MeshLoaders loaders,
        Path fixture,
        boolean packMinimal,
        boolean parseOnly,
        boolean profileParse,
        int warmupRuns,
        int timedRuns
    ) throws IOException {
        PackSpec packSpec = packMinimal ? Packers.realtimeMinimal() : Packers.realtime();
        List<Long> parseUs = new ArrayList<>(timedRuns);
        List<Long> pipelineUs = new ArrayList<>(timedRuns);
        List<Long> packUs = new ArrayList<>(timedRuns);
        List<Long> totalUs = new ArrayList<>(timedRuns);
        List<Long> parseScanUs = new ArrayList<>(timedRuns);
        List<Long> parseFloatUs = new ArrayList<>(timedRuns);
        List<Long> parseFaceUs = new ArrayList<>(timedRuns);
        int vertexCount = 0;
        int triangleCount = 0;

        for (int run = 0; run < warmupRuns + timedRuns; run++) {
            long t0 = System.nanoTime();

            long tp = System.nanoTime();
            MeshData mesh = loaders.load(fixture);
            long parseNs = System.nanoTime() - tp;
            if (vertexCount == 0) {
                vertexCount = mesh.vertexCount();
                triangleCount = mesh.indicesOrNull() == null ? 0 : (mesh.indicesOrNull().length / 3);
            }

            long pipelineNs = 0L;
            long packNs = 0L;
            if (!parseOnly) {
                long to = System.nanoTime();
                MeshData processed = Pipelines.realtimeFast(mesh);
                pipelineNs = System.nanoTime() - to;

                long tk = System.nanoTime();
                MeshPacker.pack(processed, packSpec);
                packNs = System.nanoTime() - tk;
            }

            long totalNs = System.nanoTime() - t0;

            if (run >= warmupRuns) {
                parseUs.add(parseNs / 1_000L);
                pipelineUs.add(pipelineNs / 1_000L);
                packUs.add(packNs / 1_000L);
                totalUs.add(totalNs / 1_000L);
                if (profileParse) {
                    FastObjMeshLoader.ParseProfile p = FastObjMeshLoader.lastParseProfile();
                    if (p != null) {
                        parseScanUs.add(p.tokenScanNs() / 1_000L);
                        parseFloatUs.add(p.floatParseNs() / 1_000L);
                        parseFaceUs.add(p.faceAssembleNs() / 1_000L);
                    }
                }
            }
        }

        long parseMedian = median(parseUs);
        long parseP95 = p95(parseUs);
        long pipelineMedian = median(pipelineUs);
        long pipelineP95 = p95(pipelineUs);
        long packMedian = median(packUs);
        long packP95 = p95(packUs);
        long totalMedian = median(totalUs);
        long totalP95 = p95(totalUs);
        long parseScanMedian = parseScanUs.isEmpty() ? 0L : median(parseScanUs);
        long parseScanP95 = parseScanUs.isEmpty() ? 0L : p95(parseScanUs);
        long parseFloatMedian = parseFloatUs.isEmpty() ? 0L : median(parseFloatUs);
        long parseFloatP95 = parseFloatUs.isEmpty() ? 0L : p95(parseFloatUs);
        long parseFaceMedian = parseFaceUs.isEmpty() ? 0L : median(parseFaceUs);
        long parseFaceP95 = parseFaceUs.isEmpty() ? 0L : p95(parseFaceUs);
        double parseUsPer1MVerts = vertexCount > 0 ? (parseMedian * 1_000_000.0) / vertexCount : 0.0;

        return new TimingStats(
            fixture.getFileName().toString(),
            parseMedian, parseP95,
            pipelineMedian, pipelineP95,
            packMedian, packP95,
            totalMedian, totalP95,
            vertexCount, triangleCount,
            parseUsPer1MVerts,
            parseScanMedian, parseScanP95,
            parseFloatMedian, parseFloatP95,
            parseFaceMedian, parseFaceP95
        );
    }

    private static int parsePositive(String raw, String label) {
        int parsed = Integer.parseInt(raw);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be > 0");
        }
        return parsed;
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

    private static void writeCsv(Path outFile, List<TimingStats> stats) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("fixture,vertex_count,triangle_count,parse_median_us,parse_p95_us,parse_us_per_1m_verts,parse_scan_median_us,parse_scan_p95_us,parse_float_median_us,parse_float_p95_us,parse_face_median_us,parse_face_p95_us,pipeline_median_us,pipeline_p95_us,pack_median_us,pack_p95_us,total_median_us,total_p95_us\n");
        for (TimingStats s : stats) {
            sb.append(s.fixture()).append(',')
                .append(s.vertexCount()).append(',')
                .append(s.triangleCount()).append(',')
                .append(s.parseMedianUs()).append(',')
                .append(s.parseP95Us()).append(',')
                .append(String.format(Locale.ROOT, "%.3f", s.parseUsPer1MVerts())).append(',')
                .append(s.parseScanMedianUs()).append(',')
                .append(s.parseScanP95Us()).append(',')
                .append(s.parseFloatMedianUs()).append(',')
                .append(s.parseFloatP95Us()).append(',')
                .append(s.parseFaceMedianUs()).append(',')
                .append(s.parseFaceP95Us()).append(',')
                .append(s.pipelineMedianUs()).append(',')
                .append(s.pipelineP95Us()).append(',')
                .append(s.packMedianUs()).append(',')
                .append(s.packP95Us()).append(',')
                .append(s.totalMedianUs()).append(',')
                .append(s.totalP95Us()).append('\n');
        }
        Files.writeString(outFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String formatUs(long us) {
        if (us >= 1_000L) {
            return String.format(Locale.ROOT, "%.3f ms", us / 1_000.0);
        }
        return us + " us";
    }

    private record TimingStats(
        String fixture,
        long parseMedianUs,
        long parseP95Us,
        long pipelineMedianUs,
        long pipelineP95Us,
        long packMedianUs,
        long packP95Us,
        long totalMedianUs,
        long totalP95Us,
        int vertexCount,
        int triangleCount,
        double parseUsPer1MVerts,
        long parseScanMedianUs,
        long parseScanP95Us,
        long parseFloatMedianUs,
        long parseFloatP95Us,
        long parseFaceMedianUs,
        long parseFaceP95Us
    ) {
    }
}
