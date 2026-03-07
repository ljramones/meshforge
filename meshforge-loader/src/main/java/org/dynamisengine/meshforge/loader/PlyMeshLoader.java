package org.dynamisengine.meshforge.loader;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ASCII PLY loader: positions + face triangulation.
 */
public final class PlyMeshLoader {
    private PlyMeshLoader() {
    }

    /**
     * Loads load.
     * @param path parameter value
     * @return resulting value
     */
    public static MeshData load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return load(reader);
        }
    }

    /**
     * Loads load.
     * @param reader parameter value
     * @return resulting value
     */
    public static MeshData load(Reader reader) throws IOException {
        BufferedReader br = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);

        String first = br.readLine();
        if (first == null || !first.trim().equals("ply")) {
            throw new IOException("Invalid PLY header");
        }

        boolean ascii = false;
        int vertexCount = 0;
        int faceCount = 0;

        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("format ")) {
                ascii = line.contains("ascii");
            } else if (line.startsWith("element vertex ")) {
                String[] t = line.split("\\s+");
                vertexCount = Integer.parseInt(t[2]);
            } else if (line.startsWith("element face ")) {
                String[] t = line.split("\\s+");
                faceCount = Integer.parseInt(t[2]);
            } else if (line.equals("end_header")) {
                break;
            }
        }

        if (!ascii) {
            throw new IOException("Only ASCII PLY is supported in v1");
        }
        if (vertexCount < 0 || faceCount < 0) {
            throw new IOException("PLY counts must be >= 0");
        }
        if (vertexCount == Integer.MAX_VALUE) {
            throw new IOException("PLY vertexCount exceeds supported limit: " + vertexCount);
        }

        final float[] positions;
        try {
            positions = new float[Math.multiplyExact(vertexCount, 3)];
        } catch (ArithmeticException ex) {
            throw new IOException("PLY vertex buffer size overflow for vertexCount=" + vertexCount, ex);
        }
        for (int i = 0; i < vertexCount; i++) {
            line = br.readLine();
            if (line == null) {
                throw new IOException("Unexpected EOF while reading PLY vertices");
            }
            String[] t = line.trim().split("\\s+");
            if (t.length < 3) {
                throw new IOException("Invalid PLY vertex line: " + line);
            }
            int p = i * 3;
            positions[p] = Float.parseFloat(t[0]);
            positions[p + 1] = Float.parseFloat(t[1]);
            positions[p + 2] = Float.parseFloat(t[2]);
        }

        List<Integer> indexList = new ArrayList<>();
        for (int i = 0; i < faceCount; i++) {
            line = br.readLine();
            if (line == null) {
                throw new IOException("Unexpected EOF while reading PLY faces");
            }
            String[] t = line.trim().split("\\s+");
            if (t.length < 4) {
                continue;
            }
            int n = Integer.parseInt(t[0]);
            if (n < 3 || t.length < n + 1) {
                continue;
            }
            int firstIdx = Integer.parseInt(t[1]);
            int prev = Integer.parseInt(t[2]);
            for (int k = 3; k <= n; k++) {
                int curr = Integer.parseInt(t[k]);
                indexList.add(firstIdx);
                indexList.add(prev);
                indexList.add(curr);
                prev = curr;
            }
        }

        int[] indices = toIntArray(indexList);
        validateIndices(indices, vertexCount);
        return createMesh(positions, indices);
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
                throw new IOException("PLY face index out of bounds at " + i + ": " + index + " (vertexCount=" + vertexCount + ")");
            }
        }
    }
}
