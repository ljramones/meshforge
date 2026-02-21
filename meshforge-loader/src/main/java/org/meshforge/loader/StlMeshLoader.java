package org.meshforge.loader;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal STL loader. Supports ASCII STL in v1.
 */
public final class StlMeshLoader {
    private StlMeshLoader() {
    }

    public static MeshData load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return load(reader);
        }
    }

    public static MeshData load(Reader reader) throws IOException {
        BufferedReader br = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);

        List<Float> positions = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        String line;
        int triVertexBase = 0;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("vertex ")) {
                String[] t = line.split("\\s+");
                if (t.length < 4) {
                    throw new IOException("Invalid STL vertex line: " + line);
                }
                positions.add(Float.parseFloat(t[1]));
                positions.add(Float.parseFloat(t[2]));
                positions.add(Float.parseFloat(t[3]));

                int vertexIndex = positions.size() / 3 - 1;
                indices.add(vertexIndex);

                if ((indices.size() - triVertexBase) == 3) {
                    triVertexBase += 3;
                }
            }
        }

        return createMesh(toFloatArray(positions), toIntArray(indices));
    }

    private static MeshData createMesh(float[] positions, int[] indices) {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        int vertexCount = positions.length / 3;
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            vertexCount,
            indices,
            indices.length == 0 ? List.of() : List.of(new Submesh(0, indices.length, "default"))
        );

        var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        for (int i = 0; i < vertexCount; i++) {
            int p = i * 3;
            pos.set3f(i, positions[p], positions[p + 1], positions[p + 2]);
        }
        return mesh;
    }

    private static float[] toFloatArray(List<Float> values) {
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
