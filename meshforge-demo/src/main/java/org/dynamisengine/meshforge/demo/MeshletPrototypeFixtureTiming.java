package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.bounds.Aabbf;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.ops.optimize.MeshletClusters;
import org.dynamisengine.meshforge.pack.buffer.Meshlet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Minimal meshlet prototype timing and culling-granularity experiment.
 */
public final class MeshletPrototypeFixtureTiming {
    private static final int DEFAULT_WARMUP = 2;
    private static final int DEFAULT_RUNS = 9;
    private static final int DEFAULT_MAX_VERTS = 64;
    private static final int DEFAULT_MAX_TRIS = 64;

    private MeshletPrototypeFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureFilter = null;
        int warmup = DEFAULT_WARMUP;
        int runs = DEFAULT_RUNS;
        int maxVerts = DEFAULT_MAX_VERTS;
        int maxTris = DEFAULT_MAX_TRIS;

        for (String arg : args) {
            if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            } else if (arg.startsWith("--warmup=")) {
                warmup = parsePositive(arg.substring("--warmup=".length()), "warmup");
            } else if (arg.startsWith("--runs=")) {
                runs = parsePositive(arg.substring("--runs=".length()), "runs");
            } else if (arg.startsWith("--max-verts=")) {
                maxVerts = parsePositive(arg.substring("--max-verts=".length()), "max-verts");
            } else if (arg.startsWith("--max-tris=")) {
                maxTris = parsePositive(arg.substring("--max-tris=".length()), "max-tris");
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
            .filter(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return name.contains("revithouse") || name.contains("dragon");
            })
            .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
            .toList();

        if (fixtures.isEmpty()) {
            System.out.println("No fixtures matched (expected RevitHouse/dragon unless --fixture is used).");
            return;
        }

        MeshLoaders loaders = MeshLoaders.defaultsFast();
        List<Row> rows = new ArrayList<>();

        for (Path fixture : fixtures) {
            rows.add(measureFixture(loaders, fixture, warmup, runs, maxVerts, maxTris));
        }

        System.out.println("meshlet prototype timing + culling granularity");
        System.out.printf(Locale.ROOT, "warmup=%d runs=%d maxVerts=%d maxTris=%d%n", warmup, runs, maxVerts, maxTris);
        System.out.println();
        System.out.println("| Fixture | Triangles | Meshlets | Gen ms (median) | Gen ms (p95) | Whole Visible Tris | Meshlet Visible Tris | Triangle Reduction | Meshlet Coverage |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (Row row : rows) {
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %d | %d | %.3f | %.3f | %d | %d | %.2f%% | %.2f%% |%n",
                row.fixture,
                row.totalTriangles,
                row.meshletCount,
                row.genMedianMs,
                row.genP95Ms,
                row.wholeVisibleTriangles,
                row.meshletVisibleTriangles,
                row.triangleReductionPct,
                row.meshletCoveragePct
            );
        }
    }

    private static Row measureFixture(
        MeshLoaders loaders,
        Path fixture,
        int warmup,
        int runs,
        int maxVerts,
        int maxTris
    ) throws Exception {
        int total = warmup + runs;
        List<Long> genNs = new ArrayList<>(runs);

        List<Meshlet> lastMeshlets = List.of();
        int totalTriangles = 0;

        for (int i = 0; i < total; i++) {
            MeshData loaded = loaders.load(fixture);
            MeshData processed = Pipelines.realtimeFast(loaded);
            int[] indices = processed.indicesOrNull();
            if (indices == null || indices.length == 0) {
                throw new IllegalStateException("Fixture has no indices: " + fixture);
            }

            long start = System.nanoTime();
            List<Meshlet> meshlets = MeshletClusters.buildMeshlets(processed, indices, maxVerts, maxTris);
            long end = System.nanoTime();

            if (i >= warmup) {
                genNs.add(end - start);
            }
            lastMeshlets = meshlets;
            totalTriangles = indices.length / 3;
        }

        Aabbf global = union(lastMeshlets);
        Aabbf view = centeredWindow(global, 0.5f);

        int wholeVisible = intersects(global, view) ? totalTriangles : 0;
        int meshletVisible = 0;
        int visibleMeshlets = 0;
        for (Meshlet meshlet : lastMeshlets) {
            if (intersects(meshlet.bounds(), view)) {
                meshletVisible += meshlet.triangleCount();
                visibleMeshlets++;
            }
        }

        double reduction = wholeVisible == 0
            ? 0.0
            : (1.0 - (meshletVisible / (double) wholeVisible)) * 100.0;
        double coverage = lastMeshlets.isEmpty()
            ? 0.0
            : (visibleMeshlets / (double) lastMeshlets.size()) * 100.0;

        return new Row(
            fixture.getFileName().toString(),
            totalTriangles,
            lastMeshlets.size(),
            toMs(median(genNs)),
            toMs(p95(genNs)),
            wholeVisible,
            meshletVisible,
            reduction,
            coverage
        );
    }

    private static Aabbf union(List<Meshlet> meshlets) {
        if (meshlets.isEmpty()) {
            return new Aabbf(0f, 0f, 0f, 0f, 0f, 0f);
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (Meshlet meshlet : meshlets) {
            Aabbf b = meshlet.bounds();
            if (b.minX() < minX) minX = b.minX();
            if (b.minY() < minY) minY = b.minY();
            if (b.minZ() < minZ) minZ = b.minZ();
            if (b.maxX() > maxX) maxX = b.maxX();
            if (b.maxY() > maxY) maxY = b.maxY();
            if (b.maxZ() > maxZ) maxZ = b.maxZ();
        }

        return new Aabbf(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Aabbf centeredWindow(Aabbf global, float ratio) {
        float cx = (global.minX() + global.maxX()) * 0.5f;
        float cy = (global.minY() + global.maxY()) * 0.5f;
        float cz = (global.minZ() + global.maxZ()) * 0.5f;

        float hx = (global.maxX() - global.minX()) * 0.5f * ratio;
        float hy = (global.maxY() - global.minY()) * 0.5f * ratio;
        float hz = (global.maxZ() - global.minZ()) * 0.5f * ratio;

        return new Aabbf(cx - hx, cy - hy, cz - hz, cx + hx, cy + hy, cz + hz);
    }

    private static boolean intersects(Aabbf a, Aabbf b) {
        return a.maxX() >= b.minX() && a.minX() <= b.maxX()
            && a.maxY() >= b.minY() && a.minY() <= b.maxY()
            && a.maxZ() >= b.minZ() && a.minZ() <= b.maxZ();
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

    private record Row(
        String fixture,
        int totalTriangles,
        int meshletCount,
        double genMedianMs,
        double genP95Ms,
        int wholeVisibleTriangles,
        int meshletVisibleTriangles,
        double triangleReductionPct,
        double meshletCoveragePct
    ) {
    }
}
