package org.meshforge.examples;

import org.meshforge.api.Ops;
import org.meshforge.api.Packers;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;
import org.meshforge.ops.pipeline.MeshPipeline;
import org.meshforge.pack.buffer.PackedMesh;
import org.meshforge.pack.packer.MeshPacker;

import java.util.List;

public final class CubePipelineExample {
    private CubePipelineExample() {
    }

    public static void main(String[] args) {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .build();

        int[] indices = {
            0, 1, 2, 2, 3, 0,
            4, 5, 6, 6, 7, 4
        };

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            8,
            indices,
            List.of(new Submesh(0, indices.length, "default"))
        );

        float[] positions = {
            -1, -1, -1,
             1, -1, -1,
             1,  1, -1,
            -1,  1, -1,
            -1, -1,  1,
             1, -1,  1,
             1,  1,  1,
            -1,  1,  1
        };

        var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        for (int i = 0; i < 8; i++) {
            int p = i * 3;
            pos.set3f(i, positions[p], positions[p + 1], positions[p + 2]);
        }

        MeshData optimized = MeshPipeline.run(mesh, Ops.optimizeVertexCache());
        PackedMesh packed = MeshPacker.pack(optimized, Packers.realtime());

        System.out.println("Packed stride: " + packed.layout().strideBytes());
        System.out.println("Packed vertex bytes: " + packed.vertexBuffer().capacity());
        System.out.println("Index type: " + (packed.indexBuffer() == null ? "none" : packed.indexBuffer().type()));
    }
}
