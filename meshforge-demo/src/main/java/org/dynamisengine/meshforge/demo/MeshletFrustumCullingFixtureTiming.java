package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.core.bounds.Aabbf;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.mgi.MgiMeshDataCodec;
import org.dynamisengine.meshforge.mgi.MgiMeshletBounds;
import org.dynamisengine.meshforge.mgi.MgiMeshletData;
import org.dynamisengine.meshforge.ops.cull.MeshletFrustumCuller;
import org.dynamisengine.meshforge.ops.cull.ViewFrustum;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * CPU meshlet frustum culling benchmark using prebaked meshlet metadata.
 */
public final class MeshletFrustumCullingFixtureTiming {
    private static final int DEFAULT_WARMUP = 2;
    private static final int DEFAULT_RUNS = 9;
    private static final int DEFAULT_MAX_VERTS = 64;
    private static final int DEFAULT_MAX_TRIS = 64;
    private static final float DEFAULT_WINDOW_RATIO = 0.5f;

    private MeshletFrustumCullingFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureFilter = null;
        int warmup = DEFAULT_WARMUP;
        int runs = DEFAULT_RUNS;
        int maxVerts = DEFAULT_MAX_VERTS;
        int maxTris = DEFAULT_MAX_TRIS;
        float windowRatio = DEFAULT_WINDOW_RATIO;

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
            } else if (arg.startsWith("--window-ratio=")) {
                windowRatio = parsePositiveFloat(arg.substring("--window-ratio=".length()), "window-ratio");
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
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        List<Row> rows = new ArrayList<>();

        for (Path fixture : fixtures) {
            rows.add(measureFixture(loaders, codec, fixture, warmup, runs, maxVerts, maxTris, windowRatio));
        }

        System.out.println("meshlet frustum culling (cpu, prebaked meshlet bounds)");
        System.out.printf(
            Locale.ROOT,
            "warmup=%d runs=%d maxVerts=%d maxTris=%d windowRatio=%.2f scenario=centered-axis-aligned-window usesPrebakedBounds=true%n",
            warmup,
            runs,
            maxVerts,
            maxTris,
            windowRatio
        );
        System.out.println();
        System.out.println("| Fixture | Total Meshlets | Visible Meshlets | Total Triangles | Visible Triangles | Triangle Reduction | Culling Median ms | Culling p95 ms |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|");
        for (Row row : rows) {
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %d | %d | %d | %d | %.2f%% | %.3f | %.3f |%n",
                row.fixture,
                row.totalMeshlets,
                row.visibleMeshlets,
                row.totalTriangles,
                row.visibleTriangles,
                row.triangleReductionPct,
                row.cullMedianMs,
                row.cullP95Ms
            );
        }
    }

    private static Row measureFixture(
        MeshLoaders loaders,
        MgiMeshDataCodec codec,
        Path fixture,
        int warmup,
        int runs,
        int maxVerts,
        int maxTris,
        float windowRatio
    ) throws Exception {
        Path sidecar = ensureMeshletSidecar(loaders, codec, fixture, maxVerts, maxTris);
        MgiMeshDataCodec.RuntimeDecodeResult decoded = codec.readForRuntime(Files.readAllBytes(sidecar));
        if (!decoded.meshletDataPresent()) {
            throw new IllegalStateException("Expected meshlet metadata in sidecar: " + sidecar);
        }

        MgiMeshletData meshlets = decoded.meshletDataOrNull();
        List<Aabbf> meshletBounds = toAabbList(meshlets.bounds());
        int[] triangleCounts = descriptorTriangleCounts(meshlets);

        Aabbf global = union(meshletBounds);
        Aabbf window = centeredWindow(global, windowRatio);
        ViewFrustum frustum = ViewFrustum.fromAabbWindow(window);

        int total = warmup + runs;
        List<Long> cullNs = new ArrayList<>(runs);
        MeshletFrustumCuller.CullingSummary last = null;
        for (int i = 0; i < total; i++) {
            long t0 = System.nanoTime();
            MeshletFrustumCuller.CullingSummary stats = MeshletFrustumCuller.cullSummary(meshletBounds, triangleCounts, frustum);
            long t1 = System.nanoTime();
            if (i >= warmup) {
                cullNs.add(t1 - t0);
            }
            last = stats;
        }

        return new Row(
            fixture.getFileName().toString(),
            last.totalMeshlets(),
            last.visibleMeshlets(),
            last.totalTriangles(),
            last.visibleTriangles(),
            last.triangleReductionPercent(),
            toMs(median(cullNs)),
            toMs(p95(cullNs))
        );
    }

    private static Path ensureMeshletSidecar(
        MeshLoaders loaders,
        MgiMeshDataCodec codec,
        Path sourceObj,
        int maxVerts,
        int maxTris
    ) throws Exception {
        Path sidecar = meshletSidecarPathFor(sourceObj);
        if (Files.isRegularFile(sidecar)) {
            return sidecar;
        }

        ObjToMgiMain.main(new String[] {
            "--input=" + sourceObj,
            "--overwrite",
            "--with-meshlets",
            "--meshlet-max-verts=" + maxVerts,
            "--meshlet-max-tris=" + maxTris
        });

        if (!Files.isRegularFile(sidecar)) {
            throw new IllegalStateException("Meshlet sidecar generation failed: " + sidecar);
        }
        return sidecar;
    }

    private static Path meshletSidecarPathFor(Path sourceObj) {
        String file = sourceObj.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String base = dot <= 0 ? file : file.substring(0, dot);
        return sourceObj.resolveSibling(base + ".meshlets.mgi");
    }

    private static List<Aabbf> toAabbList(List<MgiMeshletBounds> in) {
        ArrayList<Aabbf> out = new ArrayList<>(in.size());
        for (MgiMeshletBounds b : in) {
            out.add(new Aabbf(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));
        }
        return List.copyOf(out);
    }

    private static int[] descriptorTriangleCounts(MgiMeshletData data) {
        int[] out = new int[data.descriptors().size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = data.descriptors().get(i).triangleCount();
        }
        return out;
    }

    private static Aabbf union(List<Aabbf> bounds) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (Aabbf b : bounds) {
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

    private static float parsePositiveFloat(String raw, String label) {
        float parsed = Float.parseFloat(raw);
        if (!(parsed > 0f && parsed <= 1f)) {
            throw new IllegalArgumentException(label + " must be in (0, 1]");
        }
        return parsed;
    }

    private record Row(
        String fixture,
        int totalMeshlets,
        int visibleMeshlets,
        int totalTriangles,
        int visibleTriangles,
        double triangleReductionPct,
        double cullMedianMs,
        double cullP95Ms
    ) {
    }
}
