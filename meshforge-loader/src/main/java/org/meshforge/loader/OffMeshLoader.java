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
 * Minimal OFF loader: positions + polygon triangulation.
 */
public final class OffMeshLoader {
    private OffMeshLoader() {
    }

    public static MeshData load(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return load(reader);
        }
    }

    public static MeshData load(Reader reader) throws IOException {
        BufferedReader br = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);

        String magic = nextDataLine(br);
        if (magic == null || !magic.equals("OFF")) {
            throw new IOException("Invalid OFF header");
        }

        String countsLine = nextDataLine(br);
        if (countsLine == null) {
            throw new IOException("Missing OFF counts line");
        }
        String[] counts = countsLine.split("\\s+");
        if (counts.length < 2) {
            throw new IOException("Invalid OFF counts line: " + countsLine);
        }

        int vertexCount = Integer.parseInt(counts[0]);
        int faceCount = Integer.parseInt(counts[1]);
        if (vertexCount < 0 || faceCount < 0) {
            throw new IOException("OFF counts must be >= 0");
        }
        if (vertexCount == Integer.MAX_VALUE) {
            throw new IOException("OFF vertexCount exceeds supported limit: " + vertexCount);
        }

        final float[] positions;
        try {
            positions = new float[Math.multiplyExact(vertexCount, 3)];
        } catch (ArithmeticException ex) {
            throw new IOException("OFF vertex buffer size overflow for vertexCount=" + vertexCount, ex);
        }
        for (int i = 0; i < vertexCount; i++) {
            String line = nextDataLine(br);
            if (line == null) {
                throw new IOException("Unexpected EOF while reading OFF vertices");
            }
            String[] t = line.split("\\s+");
            if (t.length < 3) {
                throw new IOException("Invalid OFF vertex line: " + line);
            }
            int p = i * 3;
            positions[p] = Float.parseFloat(t[0]);
            positions[p + 1] = Float.parseFloat(t[1]);
            positions[p + 2] = Float.parseFloat(t[2]);
        }

        List<Integer> indexList = new ArrayList<>();
        for (int i = 0; i < faceCount; i++) {
            String line = nextDataLine(br);
            if (line == null) {
                throw new IOException("Unexpected EOF while reading OFF faces");
            }
            String[] t = line.split("\\s+");
            if (t.length < 4) {
                continue;
            }
            int n = Integer.parseInt(t[0]);
            if (n < 3 || t.length < n + 1) {
                continue;
            }
            int first = Integer.parseInt(t[1]);
            int prev = Integer.parseInt(t[2]);
            for (int k = 3; k <= n; k++) {
                int curr = Integer.parseInt(t[k]);
                indexList.add(first);
                indexList.add(prev);
                indexList.add(curr);
                prev = curr;
            }
        }

        int[] indices = toIntArray(indexList);
        validateIndices(indices, vertexCount);
        return createMesh(positions, indices);
    }

    private static String nextDataLine(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.isEmpty() && !line.startsWith("#")) {
                return line;
            }
        }
        return null;
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
                throw new IOException("OFF face index out of bounds at " + i + ": " + index + " (vertexCount=" + vertexCount + ")");
            }
        }
    }
}
