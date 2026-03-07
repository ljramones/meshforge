package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;

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

        MeshLoaders loaders = MeshLoaders.defaults();
        List<Row> rows = new ArrayList<>();
        for (Path file : files) {
            rows.add(timeOne(loaders, file));
        }

        System.out.println("| Fixture | Load ms (median) | Create ms (median) | Load ms / 1M verts | Create ms / 1M tris | Vertices | Triangles |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|");
        for (Row row : rows) {
            System.out.printf(
                Locale.ROOT,
                "| `%s` | %.3f | %.3f | %.3f | %.3f | %d | %d |%n",
                row.name,
                row.loadMs,
                row.createMs,
                row.loadMsPerMillionVertices,
                row.createMsPerMillionTriangles,
                row.vertices,
                row.triangles
            );
        }
    }

    private static Row timeOne(MeshLoaders loaders, Path file) throws IOException {
        double[] loadMsPass = new double[PASSES];
        double[] createMsPass = new double[PASSES];
        int vertices = 0;
        int triangles = 0;

        for (int pass = 0; pass < PASSES; pass++) {
            for (int i = 0; i < WARMUP; i++) {
                loaders.load(file);
                var mesh = loaders.load(file);
                mesh = Pipelines.realtimeFast(mesh);
                MeshPacker.pack(mesh, Packers.realtime());
            }

            double loadNsTotal = 0.0;
            for (int i = 0; i < ROUNDS; i++) {
                long start = System.nanoTime();
                loaders.load(file);
                long end = System.nanoTime();
                loadNsTotal += (end - start);
            }

            double createNsTotal = 0.0;
            for (int i = 0; i < ROUNDS; i++) {
                var mesh = loaders.load(file);
                long start = System.nanoTime();
                mesh = Pipelines.realtimeFast(mesh);
                MeshPacker.pack(mesh, Packers.realtime());
                long end = System.nanoTime();
                createNsTotal += (end - start);

                vertices = mesh.vertexCount();
                int indexCount = mesh.indicesOrNull() == null ? 0 : mesh.indicesOrNull().length;
                triangles = indexCount / 3;
            }

            loadMsPass[pass] = loadNsTotal / ROUNDS / 1_000_000.0;
            createMsPass[pass] = createNsTotal / ROUNDS / 1_000_000.0;
        }

        double loadMsMedian = median(loadMsPass);
        double createMsMedian = median(createMsPass);
        double loadMsPerMillionVertices = vertices == 0
            ? 0.0
            : loadMsMedian / (vertices / 1_000_000.0);
        double createMsPerMillionTriangles = triangles == 0
            ? 0.0
            : createMsMedian / (triangles / 1_000_000.0);

        return new Row(
            file.getFileName().toString(),
            loadMsMedian,
            createMsMedian,
            loadMsPerMillionVertices,
            createMsPerMillionTriangles,
            vertices,
            triangles
        );
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
        double loadMs,
        double createMs,
        double loadMsPerMillionVertices,
        double createMsPerMillionTriangles,
        int vertices,
        int triangles
    ) {
    }
}
