package org.dynamisengine.meshforge.api;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.builder.MeshBuilder;
import org.dynamisengine.meshforge.core.builder.MeshWriter;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.topology.Topology;

/**
 * Entry point for mesh creation helpers.
 */
public final class Meshes {

    private Meshes() {
    }

    /**
     * Creates builder.
     * @param topology parameter value
     * @return resulting value
     */
    public static MeshBuilder builder(Topology topology) {
        return new MeshBuilder(topology);
    }

    /**
     * Executes writer.
     * @param schema parameter value
     * @param vertexCount parameter value
     * @param indexCount parameter value
     * @return resulting value
     */
    public static MeshWriter writer(VertexSchema schema, int vertexCount, int indexCount) {
        return new MeshWriter(schema, vertexCount, indexCount);
    }

    /**
     * Executes cube.
     * @param extent parameter value
     * @return resulting value
     */
    public static MeshData cube(float extent) {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        float e = extent;
        return builder(Topology.TRIANGLES)
            .schema(schema)
            .vertex(-e, -e, -e)
            .vertex(e, -e, -e)
            .vertex(e, e, -e)
            .vertex(-e, e, -e)
            .vertex(-e, -e, e)
            .vertex(e, -e, e)
            .vertex(e, e, e)
            .vertex(-e, e, e)
            .triangle(0, 1, 2).triangle(2, 3, 0)
            .triangle(1, 5, 6).triangle(6, 2, 1)
            .triangle(5, 4, 7).triangle(7, 6, 5)
            .triangle(4, 0, 3).triangle(3, 7, 4)
            .triangle(3, 2, 6).triangle(6, 7, 3)
            .triangle(4, 5, 1).triangle(1, 0, 4)
            .build();
    }
}
