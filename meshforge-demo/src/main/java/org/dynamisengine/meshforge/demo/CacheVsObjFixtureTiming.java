package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.GpuGeometryUploadPlan;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.gpu.cache.RuntimeGeometryCachePolicy;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Compares cold OBJ path against runtime-geometry cache load path.
 */
public final class CacheVsObjFixtureTiming {
    private static final int WARMUP = 2;
    private static final int ROUNDS = 7;

    private CacheVsObjFixtureTiming() {
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
        if (cacheDir != null) {
            Files.createDirectories(cacheDir);
        }

        System.out.println("| Fixture | OBJ->prep->pack->bridge ms | Cache load->bridge ms | Speedup | Triangles |");
        System.out.println("|---|---:|---:|---:|---:|");
        for (Path fixture : fixtures) {
            Row row = measureFixture(loaders, spec, cacheDir, fixture, forceRebuild);
            double speedup = row.objPathMs / row.cachePathMs;
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %.3f | %.3f | %.2fx | %d |%n",
                row.name, row.objPathMs, row.cachePathMs, speedup, row.triangles
            );
        }
    }

    private static Row measureFixture(
        MeshLoaders loaders,
        PackSpec spec,
        Path cacheDir,
        Path fixture,
        boolean forceRebuild
    ) throws Exception {
        Path cacheFile = cacheDir == null
            ? RuntimeGeometryCachePolicy.sidecarPathFor(fixture)
            : cacheDir.resolve(fixture.getFileName().toString() + ".mfgc");
        MeshData source = loaders.load(fixture);
        MeshData processed = Pipelines.realtimeFast(source);
        int[] idx = processed.indicesOrNull();
        int triangles = idx == null ? 0 : idx.length / 3;
        RuntimeGeometryCacheLifecycle.loadOrBuild(fixture, cacheFile, loaders, spec, forceRebuild);

        double[] objPass = new double[3];
        double[] cachePass = new double[3];

        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < WARMUP; i++) {
                MeshData warm = loaders.load(fixture);
                MeshData warmProcessed = Pipelines.realtimeFast(warm);
                MeshPacker.RuntimePackPlan warmPlan = MeshPacker.buildRuntimePlan(warmProcessed, spec);
                MeshPacker.RuntimePackWorkspace warmWs = new MeshPacker.RuntimePackWorkspace();
                MeshPacker.packPlannedInto(warmPlan, warmWs);
                RuntimeGeometryPayload warmPayload = MeshForgeGpuBridge.payloadFromRuntimeWorkspace(warmPlan.layout(), warmWs);
                MeshForgeGpuBridge.buildUploadPlan(warmPayload);
                RuntimeGeometryPayload warmCachePayload =
                    RuntimeGeometryCacheLifecycle.loadOrBuild(fixture, cacheFile, loaders, spec, false).payload();
                MeshForgeGpuBridge.buildUploadPlan(warmCachePayload);
            }

            double objNs = 0.0;
            double cacheNs = 0.0;
            for (int i = 0; i < ROUNDS; i++) {
                long objStart = System.nanoTime();
                MeshData loaded = loaders.load(fixture);
                MeshData objProcessed = Pipelines.realtimeFast(loaded);
                MeshPacker.RuntimePackPlan objPlan = MeshPacker.buildRuntimePlan(objProcessed, spec);
                MeshPacker.RuntimePackWorkspace objWs = new MeshPacker.RuntimePackWorkspace();
                MeshPacker.packPlannedInto(objPlan, objWs);
                RuntimeGeometryPayload objPayload = MeshForgeGpuBridge.payloadFromRuntimeWorkspace(objPlan.layout(), objWs);
                GpuGeometryUploadPlan objGpuPlan = MeshForgeGpuBridge.buildUploadPlan(objPayload);
                if (objGpuPlan.vertexBinding().byteSize() <= 0) {
                    throw new IllegalStateException("Invalid obj-path upload plan");
                }
                objNs += (System.nanoTime() - objStart);

                long cacheStart = System.nanoTime();
                RuntimeGeometryPayload cachePayload =
                    RuntimeGeometryCacheLifecycle.loadOrBuild(fixture, cacheFile, loaders, spec, false).payload();
                GpuGeometryUploadPlan cacheGpuPlan = MeshForgeGpuBridge.buildUploadPlan(cachePayload);
                if (cacheGpuPlan.vertexBinding().byteSize() <= 0) {
                    throw new IllegalStateException("Invalid cache-path upload plan");
                }
                cacheNs += (System.nanoTime() - cacheStart);
            }

            objPass[pass] = objNs / ROUNDS / 1_000_000.0;
            cachePass[pass] = cacheNs / ROUNDS / 1_000_000.0;
        }

        return new Row(fixture.getFileName().toString(), median(objPass), median(cachePass), triangles);
    }

    private static double median(double[] values) {
        List<Double> sorted = new ArrayList<>(values.length);
        for (double value : values) {
            sorted.add(value);
        }
        sorted.sort(Double::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private record Row(String name, double objPathMs, double cachePathMs, int triangles) {
    }
}
