package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.gpu.cache.RuntimeGeometryCachePolicy;
import org.dynamisengine.meshforge.gpu.cache.RuntimeGeometryLoader;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Cache warm-up timing for canonical fixtures using RuntimeGeometryLoader prebuild flow.
 */
public final class CachePrebuildFixtureTiming {
    private static final int WARMUP = 1;
    private static final int ROUNDS = 5;

    private CachePrebuildFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureFilter = null;
        boolean forceRebuild = false;
        Path cacheDir = null;
        for (String arg : args) {
            if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            } else if ("--force-rebuild".equals(arg)) {
                forceRebuild = true;
            } else if (arg.startsWith("--cache-dir=")) {
                cacheDir = Path.of(arg.substring("--cache-dir=".length()).trim());
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
        RuntimeGeometryLoader runtimeLoader = new RuntimeGeometryLoader(loaders, spec);
        if (cacheDir != null) {
            Files.createDirectories(cacheDir);
        }

        System.out.println("| Fixture | Prebuild ms | Cache-hit Load ms | Prebuild Status |");
        System.out.println("|---|---:|---:|---|");
        for (Path fixture : fixtures) {
            Row row = measureFixture(runtimeLoader, fixture, cacheDir, forceRebuild);
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %.3f | %.3f | %s |%n",
                row.name, row.prebuildMs, row.cacheHitMs, row.status
            );
        }
    }

    private static Row measureFixture(
        RuntimeGeometryLoader runtimeLoader,
        Path fixture,
        Path cacheDir,
        boolean forceRebuild
    ) throws Exception {
        Path cacheFile = cacheDir == null
            ? RuntimeGeometryCachePolicy.sidecarPathFor(fixture)
            : cacheDir.resolve(fixture.getFileName().toString() + ".mfgc");

        RuntimeGeometryLoader.PrebuildStatus status = RuntimeGeometryLoader.PrebuildStatus.REUSED;
        double[] prebuildPass = new double[3];
        double[] cacheHitPass = new double[3];

        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < WARMUP; i++) {
                runtimeLoader.prebuild(fixture, cacheFile, forceRebuild && pass == 0);
                runtimeLoader.load(fixture, cacheFile, false);
            }

            double prebuildNs = 0.0;
            double cacheHitNs = 0.0;
            for (int i = 0; i < ROUNDS; i++) {
                long p0 = System.nanoTime();
                RuntimeGeometryLoader.PrebuildResult prebuild =
                    runtimeLoader.prebuild(fixture, cacheFile, forceRebuild && pass == 0 && i == 0);
                long p1 = System.nanoTime();
                RuntimeGeometryLoader.Result cacheHit = runtimeLoader.load(fixture, cacheFile, false);
                long p2 = System.nanoTime();

                status = prebuild.status();
                if (cacheHit.source() != RuntimeGeometryLoader.Source.CACHE) {
                    throw new IllegalStateException("Expected cache-hit load after prebuild for " + fixture);
                }
                prebuildNs += (p1 - p0);
                cacheHitNs += (p2 - p1);
            }

            prebuildPass[pass] = prebuildNs / ROUNDS / 1_000_000.0;
            cacheHitPass[pass] = cacheHitNs / ROUNDS / 1_000_000.0;
        }

        return new Row(
            fixture.getFileName().toString(),
            median(prebuildPass),
            median(cacheHitPass),
            status.name()
        );
    }

    private static double median(double[] values) {
        List<Double> sorted = new ArrayList<>(values.length);
        for (double value : values) {
            sorted.add(value);
        }
        sorted.sort(Double::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private record Row(String name, double prebuildMs, double cacheHitMs, String status) {
    }
}
