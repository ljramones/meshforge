package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.GpuGeometryUploadPlan;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
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
 * End-to-end seam timing: build plan -> pack planned -> build GPU upload plan.
 */
public final class GpuBridgeFixtureTiming {
    private static final int WARMUP = 2;
    private static final int ROUNDS = 7;

    private GpuBridgeFixtureTiming() {
    }

    public static void main(String[] args) throws Exception {
        String fixtureFilter = null;
        for (String arg : args) {
            if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
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

        System.out.println("| Fixture | Plan Build ms | Planned Pack ms | Upload Plan ms | End-to-End ms | Triangles |");
        System.out.println("|---|---:|---:|---:|---:|---:|");
        for (Path fixture : fixtures) {
            Row row = measureFixture(loaders, spec, fixture);
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %.3f | %.3f | %.3f | %.3f | %d |%n",
                row.name, row.planBuildMs, row.plannedPackMs, row.uploadPlanMs, row.endToEndMs, row.triangles
            );
        }
    }

    private static Row measureFixture(MeshLoaders loaders, PackSpec spec, Path fixture) throws Exception {
        double[] planBuildPass = new double[3];
        double[] plannedPackPass = new double[3];
        double[] uploadPass = new double[3];
        double[] endPass = new double[3];
        int triangles = 0;

        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < WARMUP; i++) {
                MeshData warm = loaders.load(fixture);
                MeshData processed = Pipelines.realtimeFast(warm);
                MeshPacker.RuntimePackPlan p = MeshPacker.buildRuntimePlan(processed, spec);
                MeshPacker.RuntimePackWorkspace ws = new MeshPacker.RuntimePackWorkspace();
                MeshPacker.packPlannedInto(p, ws);
                RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromRuntimeWorkspace(p.layout(), ws);
                MeshForgeGpuBridge.buildUploadPlan(payload);
            }

            double planNs = 0.0;
            double packNs = 0.0;
            double uploadNs = 0.0;
            double endNs = 0.0;
            for (int i = 0; i < ROUNDS; i++) {
                MeshData loaded = loaders.load(fixture);
                MeshData processed = Pipelines.realtimeFast(loaded);
                if (triangles == 0) {
                    int[] idx = processed.indicesOrNull();
                    triangles = idx == null ? 0 : idx.length / 3;
                }

                long t0 = System.nanoTime();
                MeshPacker.RuntimePackPlan plan = MeshPacker.buildRuntimePlan(processed, spec);
                long t1 = System.nanoTime();
                MeshPacker.RuntimePackWorkspace workspace = new MeshPacker.RuntimePackWorkspace();
                MeshPacker.packPlannedInto(plan, workspace);
                long t2 = System.nanoTime();
                RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromRuntimeWorkspace(plan.layout(), workspace);
                GpuGeometryUploadPlan gpuPlan = MeshForgeGpuBridge.buildUploadPlan(payload);
                long t3 = System.nanoTime();

                if (gpuPlan.vertexBinding().byteSize() <= 0) {
                    throw new IllegalStateException("Invalid vertex upload plan");
                }
                planNs += (t1 - t0);
                packNs += (t2 - t1);
                uploadNs += (t3 - t2);
                endNs += (t3 - t0);
            }

            planBuildPass[pass] = planNs / ROUNDS / 1_000_000.0;
            plannedPackPass[pass] = packNs / ROUNDS / 1_000_000.0;
            uploadPass[pass] = uploadNs / ROUNDS / 1_000_000.0;
            endPass[pass] = endNs / ROUNDS / 1_000_000.0;
        }

        return new Row(
            fixture.getFileName().toString(),
            median(planBuildPass),
            median(plannedPackPass),
            median(uploadPass),
            median(endPass),
            triangles
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

    private record Row(
        String name,
        double planBuildMs,
        double plannedPackMs,
        double uploadPlanMs,
        double endToEndMs,
        int triangles
    ) {
    }
}
