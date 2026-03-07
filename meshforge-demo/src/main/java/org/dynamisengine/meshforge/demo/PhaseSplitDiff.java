package org.dynamisengine.meshforge.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Compare two phase-split CSV files and print per-fixture regression deltas.
 */
public final class PhaseSplitDiff {
    private PhaseSplitDiff() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: PhaseSplitDiff <baseline.csv> <current.csv> [--max-regression-pct=<n>] [--fixture=<name-substring>]");
            return;
        }

        Path baseline = Path.of(args[0]);
        Path current = Path.of(args[1]);
        Double maxRegressionPct = null;
        String fixtureFilter = null;
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--max-regression-pct=")) {
                maxRegressionPct = Double.parseDouble(arg.substring("--max-regression-pct=".length()));
            } else if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            }
        }

        Map<String, Row> left = loadCsv(baseline);
        Map<String, Row> right = loadCsv(current);

        List<String> fixtures = new ArrayList<>();
        for (String f : left.keySet()) {
            if (right.containsKey(f)) {
                if (fixtureFilter == null || f.toLowerCase(Locale.ROOT).contains(fixtureFilter)) {
                    fixtures.add(f);
                }
            }
        }
        fixtures.sort(Comparator.comparingDouble((String f) -> pct(right.get(f).totalMedianUs, left.get(f).totalMedianUs)).reversed());

        System.out.println("Baseline: " + baseline.toAbsolutePath());
        System.out.println("Current : " + current.toAbsolutePath());
        System.out.println();
        System.out.println("Fixture deltas (positive % = slower):");
        System.out.println();
        System.out.printf("%-22s %14s %14s %12s %12s %12s %12s%n",
            "fixture", "total(base)", "total(curr)", "delta%", "parse%", "pipe%", "pack%");

        int slower = 0;
        int faster = 0;
        int gateFailures = 0;
        for (String fixture : fixtures) {
            Row b = left.get(fixture);
            Row c = right.get(fixture);
            double totalPct = pct(c.totalMedianUs, b.totalMedianUs);
            double parsePct = pct(c.parseMedianUs, b.parseMedianUs);
            double pipePct = pct(c.pipelineMedianUs, b.pipelineMedianUs);
            double packPct = pct(c.packMedianUs, b.packMedianUs);
            if (totalPct > 0.0) {
                slower++;
            } else if (totalPct < 0.0) {
                faster++;
            }

            System.out.printf(
                Locale.ROOT,
                "%-22s %14s %14s %11.2f%% %11.2f%% %11.2f%% %11.2f%%%n",
                fixture,
                fmtUs(b.totalMedianUs),
                fmtUs(c.totalMedianUs),
                totalPct, parsePct, pipePct, packPct
            );

            if (!Double.isNaN(b.parseScanMedianUs) && !Double.isNaN(c.parseScanMedianUs)) {
                double scanPct = pct(c.parseScanMedianUs, b.parseScanMedianUs);
                double floatPct = pct(c.parseFloatMedianUs, b.parseFloatMedianUs);
                double facePct = pct(c.parseFaceMedianUs, b.parseFaceMedianUs);
                System.out.printf(
                    Locale.ROOT,
                    "  parse breakdown: scan %s -> %s (%+.2f%%), float %s -> %s (%+.2f%%), face %s -> %s (%+.2f%%)%n",
                    fmtUs(b.parseScanMedianUs),
                    fmtUs(c.parseScanMedianUs),
                    scanPct,
                    fmtUs(b.parseFloatMedianUs),
                    fmtUs(c.parseFloatMedianUs),
                    floatPct,
                    fmtUs(b.parseFaceMedianUs),
                    fmtUs(c.parseFaceMedianUs),
                    facePct
                );
            }

            if (maxRegressionPct != null && totalPct > maxRegressionPct) {
                gateFailures++;
                System.out.printf(
                    Locale.ROOT,
                    "  GATE FAIL: total regression %.2f%% exceeds limit %.2f%%%n",
                    totalPct,
                    maxRegressionPct
                );
            }
        }

        System.out.println();
        System.out.printf(Locale.ROOT, "Matched fixtures: %d (faster: %d, slower: %d)%n", fixtures.size(), faster, slower);
        if (maxRegressionPct != null) {
            if (gateFailures > 0) {
                System.out.printf(Locale.ROOT, "Regression gate FAILED: %d fixture(s) exceeded %.2f%%%n", gateFailures, maxRegressionPct);
                System.exit(2);
            } else {
                System.out.printf(Locale.ROOT, "Regression gate PASSED: no fixture exceeded %.2f%%%n", maxRegressionPct);
            }
        }
    }

    private static Map<String, Row> loadCsv(Path csv) throws IOException {
        List<String> lines = Files.readAllLines(csv);
        if (lines.isEmpty()) {
            throw new IOException("CSV has no content: " + csv);
        }

        String[] header = lines.get(0).split(",", -1);
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            col.put(header[i].trim(), i);
        }
        require(col, "fixture", csv);
        require(col, "parse_median_us", csv);
        require(col, "pipeline_median_us", csv);
        require(col, "pack_median_us", csv);
        require(col, "total_median_us", csv);

        Map<String, Row> out = new HashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] t = line.split(",", -1);
            String fixture = read(t, col, "fixture");
            if (fixture.isBlank()) {
                continue;
            }
            Row row = new Row(
                parseDouble(read(t, col, "parse_median_us")),
                parseDouble(read(t, col, "pipeline_median_us")),
                parseDouble(read(t, col, "pack_median_us")),
                parseDouble(read(t, col, "total_median_us")),
                parseDoubleOpt(readOpt(t, col, "parse_scan_median_us")),
                parseDoubleOpt(readOpt(t, col, "parse_float_median_us")),
                parseDoubleOpt(readOpt(t, col, "parse_face_median_us"))
            );
            out.put(fixture, row);
        }
        return out;
    }

    private static String read(String[] tokens, Map<String, Integer> col, String key) {
        Integer idx = col.get(key);
        if (idx == null || idx < 0 || idx >= tokens.length) {
            return "";
        }
        return tokens[idx].trim();
    }

    private static String readOpt(String[] tokens, Map<String, Integer> col, String key) {
        Integer idx = col.get(key);
        if (idx == null || idx < 0 || idx >= tokens.length) {
            return "";
        }
        return tokens[idx].trim();
    }

    private static void require(Map<String, Integer> col, String key, Path csv) throws IOException {
        if (!col.containsKey(key)) {
            throw new IOException("Missing required CSV column '" + key + "' in " + csv);
        }
    }

    private static double parseDouble(String raw) {
        return Double.parseDouble(raw);
    }

    private static double parseDoubleOpt(String raw) {
        if (raw == null || raw.isBlank()) {
            return Double.NaN;
        }
        return Double.parseDouble(raw);
    }

    private static double pct(double current, double baseline) {
        if (baseline == 0.0) {
            return 0.0;
        }
        return ((current - baseline) / baseline) * 100.0;
    }

    private static String fmtUs(double us) {
        if (Double.isNaN(us)) {
            return "n/a";
        }
        if (us >= 1_000.0) {
            return String.format(Locale.ROOT, "%.3f ms", us / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.0f us", us);
    }

    private record Row(
        double parseMedianUs,
        double pipelineMedianUs,
        double packMedianUs,
        double totalMedianUs,
        double parseScanMedianUs,
        double parseFloatMedianUs,
        double parseFaceMedianUs
    ) {
    }
}
