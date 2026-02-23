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
 * Minimal OBJ loader for position and face indices.
 */
public final class ObjMeshLoader {
    private ObjMeshLoader() {
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
        while ((line = br.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("v ")) {
                String[] t = line.split("\\s+");
                if (t.length < 4) {
                    throw new IOException("Invalid vertex line: " + line);
                }
                positions.add(Float.parseFloat(t[1]));
                positions.add(Float.parseFloat(t[2]));
                positions.add(Float.parseFloat(t[3]));
            } else if (line.startsWith("f ")) {
                String[] t = line.split("\\s+");
                if (t.length < 4) {
                    continue;
                }
                int first = parseObjIndex(t[1]);
                int prev = parseObjIndex(t[2]);
                for (int i = 3; i < t.length; i++) {
                    int curr = parseObjIndex(t[i]);
                    indices.add(first);
                    indices.add(prev);
                    indices.add(curr);
                    prev = curr;
                }
            }
        }

        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        int vertexCount = positions.size() / 3;
        int[] indexData = toIntArray(indices);
        validateIndices(indexData, vertexCount);
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            vertexCount,
            indexData,
            indexData.length == 0 ? List.of() : List.of(new Submesh(0, indexData.length, "default"))
        );

        var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        for (int i = 0; i < vertexCount; i++) {
            int p = i * 3;
            pos.set3f(i, positions.get(p), positions.get(p + 1), positions.get(p + 2));
        }

        return mesh;
    }

    private static int parseObjIndex(String token) {
        String[] split = token.split("/");
        int idx = Integer.parseInt(split[0]);
        if (idx <= 0) {
            throw new IllegalArgumentException("Only positive OBJ indices are supported in v1: " + token);
        }
        return idx - 1;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static void validateIndices(int[] indices, int vertexCount) throws IOException {
        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            if (index < 0 || index >= vertexCount) {
                throw new IOException("OBJ face index out of bounds at " + i + ": " + index + " (vertexCount=" + vertexCount + ")");
            }
        }
    }
}
