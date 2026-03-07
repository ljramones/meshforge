package org.dynamisengine.meshforge.core.builder;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class MeshBuilder.
 */
public final class MeshBuilder {
    private final Topology topology;
    private VertexSchema schema = VertexSchema.builder()
        .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
        .build();

    private final List<Float> positions = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();

    /**
     * Creates a new {@code MeshBuilder} instance.
     * @param topology parameter value
     */
    public MeshBuilder(Topology topology) {
        this.topology = topology;
    }

    /**
     * Returns schema.
     * @param schema parameter value
     * @return resulting value
     */
    public MeshBuilder schema(VertexSchema schema) {
        this.schema = schema;
        return this;
    }

    /**
     * Executes vertex.
     * @param x parameter value
     * @param y parameter value
     * @param z parameter value
     * @return resulting value
     */
    public MeshBuilder vertex(float x, float y, float z) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        return this;
    }

    /**
     * Executes triangle.
     * @param a parameter value
     * @param b parameter value
     * @param c parameter value
     * @return resulting value
     */
    public MeshBuilder triangle(int a, int b, int c) {
        indices.add(a);
        indices.add(b);
        indices.add(c);
        return this;
    }

    /**
     * Creates build.
     * @return resulting value
     */
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
