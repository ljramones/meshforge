package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;

import java.nio.file.Path;

public final class MeshForgeDemo {
    private MeshForgeDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MeshForgeDemo <path-to-obj>");
            return;
        }

        Path path = Path.of(args[0]);
        var baselinePacked = loadAndPack(path, false);
        var optimizedPacked = loadAndPack(path, true);

        int packedVertexCount = optimizedPacked.layout().strideBytes() == 0
            ? 0
            : (optimizedPacked.vertexBuffer().capacity() / optimizedPacked.layout().strideBytes());
        int packedIndexCount = optimizedPacked.indexBuffer() == null ? 0 : optimizedPacked.indexBuffer().indexCount();

        System.out.println("Vertices: " + packedVertexCount);
        System.out.println("Indices: " + packedIndexCount);
        System.out.println("Packed stride: " + optimizedPacked.layout().strideBytes());
        System.out.println("Packed vertex bytes: " + optimizedPacked.vertexBuffer().capacity());
        System.out.println("Index type: " + (optimizedPacked.indexBuffer() == null ? "none" : optimizedPacked.indexBuffer().type()));
        System.out.println("Meshlets (baseline):  " + MeshletStats.summarize(baselinePacked));
        System.out.println("Meshlets (optimized): " + MeshletStats.summarize(optimizedPacked));

        double before = MeshletStats.averageCenterStep(baselinePacked);
        double after = MeshletStats.averageCenterStep(optimizedPacked);
        double improvementPct = before <= 0.0 ? 0.0 : ((before - after) / before) * 100.0;
        System.out.printf("Meshlet locality avg step: %.4f -> %.4f (improvement %.2f%%)%n", before, after, improvementPct);

        System.out.println();
        System.out.println("Cone culling simulation (baseline):");
        printConeViews(baselinePacked);
        System.out.println("Cone culling simulation (optimized):");
        printConeViews(optimizedPacked);
    }

    private static PackedMesh loadAndPack(Path path, boolean optimizeOrder) throws Exception {
        var mesh = MeshLoaders.defaults().load(path);
        mesh = Pipelines.realtimeFast(mesh);
        mesh = MeshPipeline.run(mesh, Ops.clusterizeMeshlets(128, 64));
        if (optimizeOrder) {
            mesh = MeshPipeline.run(mesh, Ops.optimizeMeshletOrder());
        }
        return MeshPacker.pack(mesh, Packers.realtimeWithMeshlets());
    }

    private static void printConeViews(PackedMesh packed) {
        System.out.println("  " + MeshletStats.summarizeConeCulling(packed, "Front", 0.0f, 0.0f, -1.0f));
        System.out.println("  " + MeshletStats.summarizeConeCulling(packed, "Right", 1.0f, 0.0f, 0.0f));
        System.out.println("  " + MeshletStats.summarizeConeCulling(packed, "Top", 0.0f, 1.0f, 0.0f));
        System.out.println("  " + MeshletStats.summarizeConeCulling(packed, "Left", -1.0f, 0.0f, 0.0f));
    }
}
