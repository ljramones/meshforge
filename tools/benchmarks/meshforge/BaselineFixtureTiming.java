package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimeMeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimePackPlan;
import org.dynamisengine.meshforge.pack.packer.RuntimePackWorkspace;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Times baseline fixture OBJ loading and mesh creation (pipeline + pack).
 */
public final class BaselineFixtureTiming {
    private static final int WARMUP = 2;
    private static final int ROUNDS = 5;
    private static final int PASSES = 3;
    private static final int REPEAT_CREATE = 16;

    private BaselineFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        Path baselineDir = Path.of("fixtures", "baseline");
        if (!Files.isDirectory(baselineDir)) {
            System.out.println("Missing directory: " + baselineDir.toAbsolutePath());
            return;
        }

        List<Path> files = Files.list(baselineDir)
            .filter(Files::isRegularFile)
            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obj"))
            .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
            .toList();

        if (files.isEmpty()) {
            System.out.println("No .obj files found in " + baselineDir.toAbsolutePath());
            return;
        }

        String fixtureFilter = null;
        int repeatCreate = REPEAT_CREATE;
        for (String arg : args) {
            if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            } else if (arg.startsWith("--repeat=")) {
                repeatCreate = parsePositive(arg.substring("--repeat=".length()), "repeat");
            }
        }

        final String fixtureFilterValue = fixtureFilter;
        MeshLoaders loaders = MeshLoaders.defaultsFast();
        List<Row> rows = new ArrayList<>();
        for (Path file : files.stream()
            .filter(p -> fixtureFilterValue == null || p.getFileName().toString().toLowerCase(Locale.ROOT).contains(fixtureFilterValue))
            .toList()) {
            rows.add(timeOne(loaders, file, repeatCreate));
        }

        System.out.println("Mode A: Cold create (Load -> Create)");
        System.out.println("| Fixture | Load ms | Create Friendly | Create Runtime | Create Planned | Friendly /1M tris | Runtime /1M tris | Planned /1M tris | Vertices | Triangles |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Row row : rows) {
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %d | %d |%n",
                row.name,
                row.loadMsMedian,
                row.coldCreateFriendlyMsMedian,
                row.coldCreateRuntimeMsMedian,
                row.coldCreatePlannedMsMedian,
                row.coldCreateFriendlyMsPerMillionTriangles,
                row.coldCreateRuntimeMsPerMillionTriangles,
                row.coldCreatePlannedMsPerMillionTriangles,
                row.vertices,
                row.triangles
            );
        }

        System.out.println();
        System.out.println("Mode B: Repeated create (Load once, repeated create)");
        System.out.printf(Locale.ROOT, "(repeat=%d per timed sample)%n", repeatCreate);
        System.out.println("| Fixture | Create Friendly | Create Runtime | Create Planned | Friendly /1M tris | Runtime /1M tris | Planned /1M tris | Vertices | Triangles |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Row row : rows) {
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %d | %d |%n",
                row.name,
                row.repeatedCreateFriendlyMsMedian,
                row.repeatedCreateRuntimeMsMedian,
                row.repeatedCreatePlannedMsMedian,
                row.repeatedCreateFriendlyMsPerMillionTriangles,
                row.repeatedCreateRuntimeMsPerMillionTriangles,
                row.repeatedCreatePlannedMsPerMillionTriangles,
                row.vertices,
                row.triangles
            );
        }
    }

    private static Row timeOne(MeshLoaders loaders, Path file, int repeatCreate) throws IOException {
        double[] loadMsPass = new double[PASSES];
        double[] coldFriendlyMsPass = new double[PASSES];
        double[] coldRuntimeMsPass = new double[PASSES];
        double[] coldPlannedMsPass = new double[PASSES];
        double[] repeatedFriendlyMsPass = new double[PASSES];
        double[] repeatedRuntimeMsPass = new double[PASSES];
        double[] repeatedPlannedMsPass = new double[PASSES];

        PackSpec spec = Packers.realtime();
        int vertices = 0;
        int triangles = 0;

        for (int pass = 0; pass < PASSES; pass++) {
            RuntimePackWorkspace warmupWorkspace = new RuntimePackWorkspace();
            for (int i = 0; i < WARMUP; i++) {
                loaders.load(file);
                var mesh = loaders.load(file);
                MeshData processed = Pipelines.realtimeFast(mesh);
                MeshPacker.pack(processed, spec);
                RuntimeMeshPacker.packInto(processed, spec, warmupWorkspace);
                RuntimePackPlan plan = RuntimeMeshPacker.buildRuntimePlan(processed, spec);
                RuntimeMeshPacker.packPlannedInto(plan, warmupWorkspace);
            }

            double loadNsTotal = 0.0;
            for (int i = 0; i < ROUNDS; i++) {
                long start = System.nanoTime();
                loaders.load(file);
                long end = System.nanoTime();
                loadNsTotal += (end - start);
            }

            double coldFriendlyNsTotal = 0.0;
            double coldRuntimeNsTotal = 0.0;
            double coldPlannedNsTotal = 0.0;
            double repeatedFriendlyNsPerOpTotal = 0.0;
            double repeatedRuntimeNsPerOpTotal = 0.0;
            double repeatedPlannedNsPerOpTotal = 0.0;

            RuntimePackWorkspace runtimeWorkspace = new RuntimePackWorkspace();
            RuntimePackWorkspace plannedWorkspace = new RuntimePackWorkspace();
            for (int i = 0; i < ROUNDS; i++) {
                MeshData coldFriendlyMesh = loaders.load(file);
                long coldFriendlyStart = System.nanoTime();
                MeshData coldFriendlyProcessed = Pipelines.realtimeFast(coldFriendlyMesh);
                MeshPacker.pack(coldFriendlyProcessed, spec);
                long coldFriendlyEnd = System.nanoTime();
                coldFriendlyNsTotal += (coldFriendlyEnd - coldFriendlyStart);

                MeshData coldRuntimeMesh = loaders.load(file);
                long coldRuntimeStart = System.nanoTime();
                MeshData coldRuntimeProcessed = Pipelines.realtimeFast(coldRuntimeMesh);
                RuntimeMeshPacker.packInto(coldRuntimeProcessed, spec, runtimeWorkspace);
                long coldRuntimeEnd = System.nanoTime();
                coldRuntimeNsTotal += (coldRuntimeEnd - coldRuntimeStart);

                MeshData coldPlannedMesh = loaders.load(file);
                long coldPlannedStart = System.nanoTime();
                MeshData coldPlannedProcessed = Pipelines.realtimeFast(coldPlannedMesh);
                RuntimePackPlan coldPlan = RuntimeMeshPacker.buildRuntimePlan(coldPlannedProcessed, spec);
                RuntimeMeshPacker.packPlannedInto(coldPlan, plannedWorkspace);
                long coldPlannedEnd = System.nanoTime();
                coldPlannedNsTotal += (coldPlannedEnd - coldPlannedStart);

                vertices = coldPlannedProcessed.vertexCount();
                int indexCount = coldPlannedProcessed.indicesOrNull() == null ? 0 : coldPlannedProcessed.indicesOrNull().length;
                triangles = indexCount / 3;

                MeshData loadedOnce = loaders.load(file);
                MeshData preparedOnce = Pipelines.realtimeFast(loadedOnce);
                RuntimePackPlan repeatedPlan = RuntimeMeshPacker.buildRuntimePlan(preparedOnce, spec);

                long repeatedFriendlyStart = System.nanoTime();
                for (int it = 0; it < repeatCreate; it++) {
                    MeshPacker.pack(preparedOnce, spec);
                }
                long repeatedFriendlyEnd = System.nanoTime();
                repeatedFriendlyNsPerOpTotal += ((double) (repeatedFriendlyEnd - repeatedFriendlyStart)) / repeatCreate;

                long repeatedRuntimeStart = System.nanoTime();
                for (int it = 0; it < repeatCreate; it++) {
                    RuntimeMeshPacker.packInto(preparedOnce, spec, runtimeWorkspace);
                }
                long repeatedRuntimeEnd = System.nanoTime();
                repeatedRuntimeNsPerOpTotal += ((double) (repeatedRuntimeEnd - repeatedRuntimeStart)) / repeatCreate;

                long repeatedPlannedStart = System.nanoTime();
                for (int it = 0; it < repeatCreate; it++) {
                    RuntimeMeshPacker.packPlannedInto(repeatedPlan, plannedWorkspace);
                }
                long repeatedPlannedEnd = System.nanoTime();
                repeatedPlannedNsPerOpTotal += ((double) (repeatedPlannedEnd - repeatedPlannedStart)) / repeatCreate;
            }

            loadMsPass[pass] = loadNsTotal / ROUNDS / 1_000_000.0;
            coldFriendlyMsPass[pass] = coldFriendlyNsTotal / ROUNDS / 1_000_000.0;
            coldRuntimeMsPass[pass] = coldRuntimeNsTotal / ROUNDS / 1_000_000.0;
            coldPlannedMsPass[pass] = coldPlannedNsTotal / ROUNDS / 1_000_000.0;
            repeatedFriendlyMsPass[pass] = repeatedFriendlyNsPerOpTotal / 1_000_000.0;
            repeatedRuntimeMsPass[pass] = repeatedRuntimeNsPerOpTotal / 1_000_000.0;
            repeatedPlannedMsPass[pass] = repeatedPlannedNsPerOpTotal / 1_000_000.0;
        }

        double loadMsMedian = median(loadMsPass);
        double coldFriendlyMsMedian = median(coldFriendlyMsPass);
        double coldRuntimeMsMedian = median(coldRuntimeMsPass);
        double coldPlannedMsMedian = median(coldPlannedMsPass);
        double repeatedFriendlyMsMedian = median(repeatedFriendlyMsPass);
        double repeatedRuntimeMsMedian = median(repeatedRuntimeMsPass);
        double repeatedPlannedMsMedian = median(repeatedPlannedMsPass);

        double coldFriendlyMsPerMillionTriangles = normalizePerMillionTriangles(coldFriendlyMsMedian, triangles);
        double coldRuntimeMsPerMillionTriangles = normalizePerMillionTriangles(coldRuntimeMsMedian, triangles);
        double coldPlannedMsPerMillionTriangles = normalizePerMillionTriangles(coldPlannedMsMedian, triangles);
        double repeatedFriendlyMsPerMillionTriangles = normalizePerMillionTriangles(repeatedFriendlyMsMedian, triangles);
        double repeatedRuntimeMsPerMillionTriangles = normalizePerMillionTriangles(repeatedRuntimeMsMedian, triangles);
        double repeatedPlannedMsPerMillionTriangles = normalizePerMillionTriangles(repeatedPlannedMsMedian, triangles);

        return new Row(
            file.getFileName().toString(),
            loadMsMedian,
            coldFriendlyMsMedian,
            coldRuntimeMsMedian,
            coldPlannedMsMedian,
            repeatedFriendlyMsMedian,
            repeatedRuntimeMsMedian,
            repeatedPlannedMsMedian,
            coldFriendlyMsPerMillionTriangles,
            coldRuntimeMsPerMillionTriangles,
            coldPlannedMsPerMillionTriangles,
            repeatedFriendlyMsPerMillionTriangles,
            repeatedRuntimeMsPerMillionTriangles,
            repeatedPlannedMsPerMillionTriangles,
            vertices,
            triangles
        );
    }

    private static double normalizePerMillionTriangles(double ms, int triangles) {
        if (triangles == 0) {
            return 0.0;
        }
        return ms / (triangles / 1_000_000.0);
    }

    private static int parsePositive(String raw, String label) {
        int parsed = Integer.parseInt(raw);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be > 0");
        }
        return parsed;
    }

    private static double median(double[] values) {
        double[] copy = values.clone();
        java.util.Arrays.sort(copy);
        int mid = copy.length / 2;
        if ((copy.length % 2) == 1) {
            return copy[mid];
        }
        return (copy[mid - 1] + copy[mid]) * 0.5;
    }

    private record Row(
        String name,
        double loadMsMedian,
        double coldCreateFriendlyMsMedian,
        double coldCreateRuntimeMsMedian,
        double coldCreatePlannedMsMedian,
        double repeatedCreateFriendlyMsMedian,
        double repeatedCreateRuntimeMsMedian,
        double repeatedCreatePlannedMsMedian,
        double coldCreateFriendlyMsPerMillionTriangles,
        double coldCreateRuntimeMsPerMillionTriangles,
        double coldCreatePlannedMsPerMillionTriangles,
        double repeatedCreateFriendlyMsPerMillionTriangles,
        double repeatedCreateRuntimeMsPerMillionTriangles,
        double repeatedCreatePlannedMsPerMillionTriangles,
        int vertices,
        int triangles
    ) {
    }
}
