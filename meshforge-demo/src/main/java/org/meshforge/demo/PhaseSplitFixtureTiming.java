package org.meshforge.demo;

import org.meshforge.api.Packers;
import org.meshforge.api.Pipelines;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.loader.MeshLoaders;
import org.meshforge.pack.packer.MeshPacker;

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
    private static final int WARMUP_RUNS = 3;
    private static final int TIMED_RUNS = 7;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private PhaseSplitFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        boolean fastLoader = true;
        for (String arg : args) {
            if ("--legacy".equals(arg)) {
                fastLoader = false;
            } else if ("--fast".equals(arg)) {
                fastLoader = true;
            }
        }

        Path fixturesDir = Path.of("fixtures", "baseline");
        if (!Files.isDirectory(fixturesDir)) {
            System.out.println("Missing baseline fixtures directory: " + fixturesDir.toAbsolutePath());
            return;
        }

        List<Path> fixtures = Files.list(fixturesDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obj"))
            .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
            .toList();

        if (fixtures.isEmpty()) {
            System.out.println("No OBJ fixtures found in " + fixturesDir.toAbsolutePath());
            return;
        }

        MeshLoaders loaders = fastLoader ? MeshLoaders.defaultsFast() : MeshLoaders.defaultsLegacy();
        List<TimingStats> all = new ArrayList<>();

        System.out.println(
            "Phase-split timing (" + (fastLoader ? "fast" : "legacy") +
                " loader, median + p95 over " + TIMED_RUNS +
                " timed runs after " + WARMUP_RUNS + " warmup runs)"
        );
        System.out.println();
        for (Path fixture : fixtures) {
            TimingStats stats = runFixture(loaders, fixture);
            all.add(stats);
            System.out.printf(
                Locale.ROOT,
                "%-20s parse: %4d ms (p95 %4d) | pipeline: %4d ms (p95 %4d) | pack: %4d ms (p95 %4d) | total: %4d ms (p95 %4d)%n",
                stats.fixture() + ":",
                stats.parseMedianMs(), stats.parseP95Ms(),
                stats.pipelineMedianMs(), stats.pipelineP95Ms(),
                stats.packMedianMs(), stats.packP95Ms(),
                stats.totalMedianMs(), stats.totalP95Ms()
            );
        }

        Path outDir = Path.of("perf", "results");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("phase-split-" + (fastLoader ? "fast" : "legacy") + "-" + LocalDateTime.now().format(TS) + ".csv");
        writeCsv(outFile, all);
        System.out.println();
        System.out.println("Results written to: " + outFile.toAbsolutePath());
    }

    private static TimingStats runFixture(MeshLoaders loaders, Path fixture) throws IOException {
        List<Long> parseMs = new ArrayList<>(TIMED_RUNS);
        List<Long> pipelineMs = new ArrayList<>(TIMED_RUNS);
        List<Long> packMs = new ArrayList<>(TIMED_RUNS);
        List<Long> totalMs = new ArrayList<>(TIMED_RUNS);

        for (int run = 0; run < WARMUP_RUNS + TIMED_RUNS; run++) {
            long t0 = System.nanoTime();

            long tp = System.nanoTime();
            MeshData mesh = loaders.load(fixture);
            long parseNs = System.nanoTime() - tp;

            long to = System.nanoTime();
            MeshData processed = Pipelines.realtimeFast(mesh);
            long pipelineNs = System.nanoTime() - to;

            long tk = System.nanoTime();
            MeshPacker.pack(processed, Packers.realtime());
            long packNs = System.nanoTime() - tk;

            long totalNs = System.nanoTime() - t0;

            if (run >= WARMUP_RUNS) {
                parseMs.add(parseNs / 1_000_000L);
                pipelineMs.add(pipelineNs / 1_000_000L);
                packMs.add(packNs / 1_000_000L);
                totalMs.add(totalNs / 1_000_000L);
            }
        }

        return new TimingStats(
            fixture.getFileName().toString(),
            median(parseMs), p95(parseMs),
            median(pipelineMs), p95(pipelineMs),
            median(packMs), p95(packMs),
            median(totalMs), p95(totalMs)
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

    private static void writeCsv(Path outFile, List<TimingStats> stats) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("fixture,parse_median_ms,parse_p95_ms,pipeline_median_ms,pipeline_p95_ms,pack_median_ms,pack_p95_ms,total_median_ms,total_p95_ms\n");
        for (TimingStats s : stats) {
            sb.append(s.fixture()).append(',')
                .append(s.parseMedianMs()).append(',')
                .append(s.parseP95Ms()).append(',')
                .append(s.pipelineMedianMs()).append(',')
                .append(s.pipelineP95Ms()).append(',')
                .append(s.packMedianMs()).append(',')
                .append(s.packP95Ms()).append(',')
                .append(s.totalMedianMs()).append(',')
                .append(s.totalP95Ms()).append('\n');
        }
        Files.writeString(outFile, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private record TimingStats(
        String fixture,
        long parseMedianMs,
        long parseP95Ms,
        long pipelineMedianMs,
        long pipelineP95Ms,
        long packMedianMs,
        long packP95Ms,
        long totalMedianMs,
        long totalP95Ms
    ) {
    }
}
