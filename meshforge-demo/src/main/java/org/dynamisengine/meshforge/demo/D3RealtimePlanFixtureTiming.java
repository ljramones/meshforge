package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimeMeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimePackWorkspace;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * D3 baseline: compare runtime create vs planned realtime pipeline create.
 */
public final class D3RealtimePlanFixtureTiming {
    private static final int WARMUP = 2;
    private static final int ROUNDS = 7;
    private static final int DEFAULT_REPEAT = 8;

    private D3RealtimePlanFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureFilter = null;
        int repeat = DEFAULT_REPEAT;
        for (String arg : args) {
            if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            } else if (arg.startsWith("--repeat=")) {
                repeat = parsePositive(arg.substring("--repeat=".length()), "repeat");
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

        System.out.println("D3 Realtime Plan Timing");
        System.out.println("| Fixture | Runtime (cold) | Planned incl-build (cold) | Planned reuse (cold) | Runtime (repeat) | Planned incl-build (repeat) | Planned reuse (repeat) | Triangles |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|");

        for (Path fixture : fixtures) {
            Row row = measureFixture(loaders, spec, fixture, repeat);
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %d |%n",
                row.name,
                row.runtimeColdMs,
                row.plannedBuildColdMs,
                row.plannedReuseColdMs,
                row.runtimeRepeatMs,
                row.plannedBuildRepeatMs,
                row.plannedReuseRepeatMs,
                row.triangles
            );
        }
    }

    private static Row measureFixture(MeshLoaders loaders, PackSpec spec, Path fixture, int repeat) throws Exception {
        double[] runtimeColdPass = new double[3];
        double[] plannedBuildColdPass = new double[3];
        double[] plannedReuseColdPass = new double[3];
        double[] runtimeRepeatPass = new double[3];
        double[] plannedBuildRepeatPass = new double[3];
        double[] plannedReuseRepeatPass = new double[3];

        int triangles = 0;

        for (int pass = 0; pass < 3; pass++) {
            RuntimePackWorkspace warmRuntimeWs = new RuntimePackWorkspace();
            Pipelines.RuntimeRealtimeWorkspace warmPlanWs = new Pipelines.RuntimeRealtimeWorkspace();
            for (int i = 0; i < WARMUP; i++) {
                MeshData warmMesh = loaders.load(fixture);
                MeshData warmRuntimeProcessed = Pipelines.realtimeFast(warmMesh);
                RuntimeMeshPacker.packInto(warmRuntimeProcessed, spec, warmRuntimeWs);

                MeshData warmPlanMesh = loaders.load(fixture);
                Pipelines.RuntimeRealtimePlan warmPlan = Pipelines.buildRealtimeFastPlan(warmPlanMesh, spec);
                Pipelines.executeRealtimeFastPlanInto(warmPlanMesh, warmPlan, warmPlanWs);
            }

            double runtimeColdNs = 0.0;
            double plannedBuildColdNs = 0.0;
            double plannedReuseColdNs = 0.0;
            double runtimeRepeatNs = 0.0;
            double plannedBuildRepeatNs = 0.0;
            double plannedReuseRepeatNs = 0.0;

            MeshData prototype = loaders.load(fixture);
            Pipelines.RuntimeRealtimePlan reusedPlan = Pipelines.buildRealtimeFastPlan(prototype, spec);
            Pipelines.RuntimeRealtimeWorkspace reuseWorkspace = new Pipelines.RuntimeRealtimeWorkspace();

            for (int i = 0; i < ROUNDS; i++) {
                RuntimePackWorkspace runtimeWs = new RuntimePackWorkspace();
                MeshData runtimeMesh = loaders.load(fixture);
                long runtimeStart = System.nanoTime();
                MeshData runtimeProcessed = Pipelines.realtimeFast(runtimeMesh);
                RuntimeMeshPacker.packInto(runtimeProcessed, spec, runtimeWs);
                runtimeColdNs += (System.nanoTime() - runtimeStart);
                if (triangles == 0) {
                    int[] idx = runtimeProcessed.indicesOrNull();
                    triangles = idx == null ? 0 : idx.length / 3;
                }

                MeshData plannedBuildMesh = loaders.load(fixture);
                Pipelines.RuntimeRealtimeWorkspace buildWorkspace = new Pipelines.RuntimeRealtimeWorkspace();
                long plannedBuildStart = System.nanoTime();
                Pipelines.RuntimeRealtimePlan built = Pipelines.buildRealtimeFastPlan(plannedBuildMesh, spec);
                Pipelines.executeRealtimeFastPlanInto(plannedBuildMesh, built, buildWorkspace);
                plannedBuildColdNs += (System.nanoTime() - plannedBuildStart);

                MeshData plannedReuseMesh = loaders.load(fixture);
                long plannedReuseStart = System.nanoTime();
                Pipelines.executeRealtimeFastPlanInto(plannedReuseMesh, reusedPlan, reuseWorkspace);
                plannedReuseColdNs += (System.nanoTime() - plannedReuseStart);

                runtimeRepeatNs += repeatLaneRuntime(loaders, fixture, spec, repeat);
                plannedBuildRepeatNs += repeatLanePlannedBuild(loaders, fixture, spec, repeat);
                plannedReuseRepeatNs += repeatLanePlannedReuse(loaders, fixture, reusedPlan, reuseWorkspace, repeat);
            }

            runtimeColdPass[pass] = runtimeColdNs / ROUNDS / 1_000_000.0;
            plannedBuildColdPass[pass] = plannedBuildColdNs / ROUNDS / 1_000_000.0;
            plannedReuseColdPass[pass] = plannedReuseColdNs / ROUNDS / 1_000_000.0;
            runtimeRepeatPass[pass] = runtimeRepeatNs / ROUNDS / 1_000_000.0;
            plannedBuildRepeatPass[pass] = plannedBuildRepeatNs / ROUNDS / 1_000_000.0;
            plannedReuseRepeatPass[pass] = plannedReuseRepeatNs / ROUNDS / 1_000_000.0;
        }

        return new Row(
            fixture.getFileName().toString(),
            median(runtimeColdPass),
            median(plannedBuildColdPass),
            median(plannedReuseColdPass),
            median(runtimeRepeatPass),
            median(plannedBuildRepeatPass),
            median(plannedReuseRepeatPass),
            triangles
        );
    }

    private static double repeatLaneRuntime(MeshLoaders loaders, Path fixture, PackSpec spec, int repeat) throws Exception {
        RuntimePackWorkspace ws = new RuntimePackWorkspace();
        long totalNs = 0L;
        for (int it = 0; it < repeat; it++) {
            MeshData mesh = loaders.load(fixture);
            long start = System.nanoTime();
            MeshData processed = Pipelines.realtimeFast(mesh);
            RuntimeMeshPacker.packInto(processed, spec, ws);
            totalNs += (System.nanoTime() - start);
        }
        return ((double) totalNs) / repeat;
    }

    private static double repeatLanePlannedBuild(MeshLoaders loaders, Path fixture, PackSpec spec, int repeat) throws Exception {
        long totalNs = 0L;
        for (int it = 0; it < repeat; it++) {
            MeshData mesh = loaders.load(fixture);
            Pipelines.RuntimeRealtimeWorkspace ws = new Pipelines.RuntimeRealtimeWorkspace();
            long start = System.nanoTime();
            Pipelines.RuntimeRealtimePlan built = Pipelines.buildRealtimeFastPlan(mesh, spec);
            Pipelines.executeRealtimeFastPlanInto(mesh, built, ws);
            totalNs += (System.nanoTime() - start);
        }
        return ((double) totalNs) / repeat;
    }

    private static double repeatLanePlannedReuse(
        MeshLoaders loaders,
        Path fixture,
        Pipelines.RuntimeRealtimePlan plan,
        Pipelines.RuntimeRealtimeWorkspace ws,
        int repeat
    ) throws Exception {
        long totalNs = 0L;
        for (int it = 0; it < repeat; it++) {
            MeshData mesh = loaders.load(fixture);
            long start = System.nanoTime();
            Pipelines.executeRealtimeFastPlanInto(mesh, plan, ws);
            totalNs += (System.nanoTime() - start);
        }
        return ((double) totalNs) / repeat;
    }

    private static int parsePositive(String raw, String label) {
        int parsed = Integer.parseInt(raw);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be > 0");
        }
        return parsed;
    }

    private static double median(double[] values) {
        List<Double> sorted = new ArrayList<>(values.length);
        for (double value : values) {
            sorted.add(value);
        }
        sorted.sort(Double::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private record Row(
        String name,
        double runtimeColdMs,
        double plannedBuildColdMs,
        double plannedReuseColdMs,
        double runtimeRepeatMs,
        double plannedBuildRepeatMs,
        double plannedReuseRepeatMs,
        int triangles
    ) {
    }
}
