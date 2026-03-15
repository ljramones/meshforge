package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.ops.pipeline.MeshContext;
import org.dynamisengine.meshforge.ops.pipeline.MeshOp;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * D2-focused op-level timing for Pipelines.realtimeFast() plus pack lanes.
 */
public final class RealtimePipelineHotspotTiming {
    private static final int DEFAULT_WARMUP = 2;
    private static final int DEFAULT_RUNS = 9;

    private RealtimePipelineHotspotTiming() {
    }

    public static void main(String[] args) throws Exception {
        int warmup = DEFAULT_WARMUP;
        int runs = DEFAULT_RUNS;
        String fixtureFilter = null;
        for (String arg : args) {
            if (arg.startsWith("--warmup=")) {
                warmup = parsePositive(arg.substring("--warmup=".length()), "warmup");
            } else if (arg.startsWith("--runs=")) {
                runs = parsePositive(arg.substring("--runs=".length()), "runs");
            } else if (arg.startsWith("--fixture=")) {
                fixtureFilter = arg.substring("--fixture=".length()).trim().toLowerCase(Locale.ROOT);
            }
        }

        Path fixtureDir = Path.of("fixtures", "baseline");
        if (!Files.isDirectory(fixtureDir)) {
            System.out.println("Missing baseline fixture dir: " + fixtureDir.toAbsolutePath());
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

        System.out.println("Realtime Pipeline Hotspots (median over timed runs)");
        System.out.println("warmup=" + warmup + " runs=" + runs);
        System.out.println();

        for (Path fixture : fixtures) {
            Result result = measureFixture(loaders, spec, fixture, warmup, runs);
            printFixture(result);
        }
    }

    private static Result measureFixture(MeshLoaders loaders, PackSpec spec, Path fixture, int warmup, int runs) throws IOException {
        int totalRuns = warmup + runs;

        List<Long> setupNs = new ArrayList<>(runs);
        List<Long> packIntoNs = new ArrayList<>(runs);
        List<Long> planBuildNs = new ArrayList<>(runs);
        List<Long> packPlannedNs = new ArrayList<>(runs);
        List<Long> totalCreateNs = new ArrayList<>(runs);
        Map<String, List<Long>> opNs = new LinkedHashMap<>();

        int vertices = 0;
        int triangles = 0;
        int indices = 0;
        boolean hasNormal = false;
        boolean hasUv = false;
        boolean hasTangent = false;

        for (int run = 0; run < totalRuns; run++) {
            MeshData mesh = loaders.load(fixture);
            if (vertices == 0) {
                vertices = mesh.vertexCount();
                indices = mesh.indicesOrNull() == null ? 0 : mesh.indicesOrNull().length;
                triangles = indices / 3;
                hasNormal = mesh.has(AttributeSemantic.NORMAL, 0);
                hasUv = mesh.has(AttributeSemantic.UV, 0);
                hasTangent = mesh.has(AttributeSemantic.TANGENT, 0);
            }

            long setupStart = System.nanoTime();
            MeshOp[] ops = Pipelines.realtimeFastOps(mesh);
            long setupElapsed = System.nanoTime() - setupStart;

            MeshContext context = new MeshContext();
            MeshData current = mesh;
            long pipelineNsTotal = 0L;
            Map<String, Long> runOpNs = new LinkedHashMap<>();
            for (MeshOp op : ops) {
                String opName = op.getClass().getSimpleName();
                long opStart = System.nanoTime();
                current = op.apply(current, context);
                long opElapsed = System.nanoTime() - opStart;
                runOpNs.merge(opName, opElapsed, Long::sum);
                pipelineNsTotal += opElapsed;
            }

            RuntimePackWorkspace runtimeWs = new RuntimePackWorkspace();
            long packStart = System.nanoTime();
            RuntimeMeshPacker.packInto(current, spec, runtimeWs);
            long packElapsed = System.nanoTime() - packStart;

            RuntimePackWorkspace plannedWs = new RuntimePackWorkspace();
            long planBuildStart = System.nanoTime();
            RuntimePackPlan plan = RuntimeMeshPacker.buildRuntimePlan(current, spec);
            long planBuildElapsed = System.nanoTime() - planBuildStart;

            long packPlannedStart = System.nanoTime();
            RuntimeMeshPacker.packPlannedInto(plan, plannedWs);
            long packPlannedElapsed = System.nanoTime() - packPlannedStart;

            long totalElapsed = setupElapsed + pipelineNsTotal + packElapsed;

            if (run >= warmup) {
                setupNs.add(setupElapsed);
                packIntoNs.add(packElapsed);
                planBuildNs.add(planBuildElapsed);
                packPlannedNs.add(packPlannedElapsed);
                totalCreateNs.add(totalElapsed);
                for (Map.Entry<String, Long> entry : runOpNs.entrySet()) {
                    opNs.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>(runs)).add(entry.getValue());
                }
            }
        }

        Map<String, Long> opMedianNs = new LinkedHashMap<>();
        for (Map.Entry<String, List<Long>> entry : opNs.entrySet()) {
            opMedianNs.put(entry.getKey(), median(entry.getValue()));
        }

        return new Result(
            fixture.getFileName().toString(),
            vertices,
            triangles,
            indices,
            hasNormal,
            hasUv,
            hasTangent,
            median(setupNs),
            opMedianNs,
            median(packIntoNs),
            median(planBuildNs),
            median(packPlannedNs),
            median(totalCreateNs)
        );
    }

    private static void printFixture(Result result) {
        System.out.printf(
            Locale.ROOT,
            "Fixture: %s (verts=%d tris=%d idx=%d, normal=%s uv=%s tangent=%s)%n",
            result.fixture(),
            result.vertices(),
            result.triangles(),
            result.indices(),
            result.hasNormal(),
            result.hasUv(),
            result.hasTangent()
        );
        System.out.println("| Phase | Median ms | % of total create |");
        System.out.println("|---|---:|---:|");

        double totalMs = result.totalCreateMedianNs() / 1_000_000.0;
        printRow("pipeline.setup", result.setupMedianNs(), totalMs);
        for (Map.Entry<String, Long> entry : result.opMedianNs().entrySet()) {
            printRow("pipeline." + entry.getKey(), entry.getValue(), totalMs);
        }
        printRow("pack.packInto", result.packIntoMedianNs(), totalMs);
        printRow("pack.buildRuntimePlan", result.planBuildMedianNs(), totalMs);
        printRow("pack.packPlannedInto", result.packPlannedMedianNs(), totalMs);

        System.out.printf(Locale.ROOT, "total(create runtime lane): %.3f ms%n", totalMs);
        System.out.println();
    }

    private static void printRow(String label, long ns, double totalMs) {
        double ms = ns / 1_000_000.0;
        double pct = totalMs <= 0.0 ? 0.0 : (ms / totalMs) * 100.0;
        System.out.printf(Locale.ROOT, "| %s | %.3f | %.1f%% |%n", label, ms, pct);
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

    private record Result(
        String fixture,
        int vertices,
        int triangles,
        int indices,
        boolean hasNormal,
        boolean hasUv,
        boolean hasTangent,
        long setupMedianNs,
        Map<String, Long> opMedianNs,
        long packIntoMedianNs,
        long planBuildMedianNs,
        long packPlannedMedianNs,
        long totalCreateMedianNs
    ) {
    }
}
