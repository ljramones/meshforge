package org.meshforge.demo;

import org.meshforge.api.Ops;
import org.meshforge.api.Packers;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.loader.MeshLoaders;
import org.meshforge.ops.pipeline.MeshOp;
import org.meshforge.ops.pipeline.MeshPipeline;
import org.meshforge.pack.packer.MeshPacker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class MeshForgeDemo {
    private MeshForgeDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MeshForgeDemo <path-to-obj>");
            return;
        }

        var mesh = MeshLoaders.defaults().load(Path.of(args[0]));
        List<MeshOp> ops = new ArrayList<>();
        ops.add(Ops.validate());
        ops.add(Ops.removeDegenerates());
        ops.add(Ops.weld(1.0e-6f));
        ops.add(Ops.normals(60f));
        if (mesh.has(AttributeSemantic.UV, 0)) {
            ops.add(Ops.tangents());
        }
        ops.add(Ops.optimizeVertexCache());
        ops.add(Ops.bounds());
        mesh = MeshPipeline.run(mesh, ops.toArray(MeshOp[]::new));

        var packed = MeshPacker.pack(mesh, Packers.realtime());
        System.out.println("Vertices: " + mesh.vertexCount());
        System.out.println("Indices: " + (mesh.indicesOrNull() == null ? 0 : mesh.indicesOrNull().length));
        System.out.println("Packed stride: " + packed.layout().strideBytes());
        System.out.println("Packed vertex bytes: " + packed.vertexBuffer().capacity());
        System.out.println("Index type: " + (packed.indexBuffer() == null ? "none" : packed.indexBuffer().type()));
    }
}
