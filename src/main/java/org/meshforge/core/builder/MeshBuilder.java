package org.meshforge.core.builder;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;

import java.util.ArrayList;
import java.util.List;

public final class MeshBuilder {
    private final Topology topology;
    private VertexSchema schema = VertexSchema.builder()
        .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
        .build();

    private final List<Float> positions = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();

    public MeshBuilder(Topology topology) {
        this.topology = topology;
    }

    public MeshBuilder schema(VertexSchema schema) {
        this.schema = schema;
        return this;
    }

    public MeshBuilder vertex(float x, float y, float z) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        return this;
    }

    public MeshBuilder triangle(int a, int b, int c) {
        indices.add(a);
        indices.add(b);
        indices.add(c);
        return this;
    }

    public MeshData build() {
        int vertexCount = positions.size() / 3;
        int[] indexData = indices.isEmpty() ? null : toIntArray(indices);

        List<Submesh> submeshes = indexData == null
            ? List.of()
            : List.of(new Submesh(0, indexData.length, "default"));

        MeshData mesh = new MeshData(topology, schema, vertexCount, indexData, submeshes);
        if (mesh.has(AttributeSemantic.POSITION, 0)) {
            var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
            for (int i = 0; i < vertexCount; i++) {
                int p = i * 3;
                pos.set3f(i, positions.get(p), positions.get(p + 1), positions.get(p + 2));
            }
        }
        return mesh;
    }

    private static int[] toIntArray(List<Integer> src) {
        int[] out = new int[src.size()];
        for (int i = 0; i < src.size(); i++) {
            out[i] = src.get(i);
        }
        return out;
    }
}
