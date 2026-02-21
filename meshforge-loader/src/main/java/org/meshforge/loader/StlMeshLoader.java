package org.meshforge.loader;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * STL loader. Supports ASCII and binary STL in v1.
 */
public final class StlMeshLoader {
    private static final int BINARY_HEADER_BYTES = 80;
    private static final int BINARY_TRIANGLE_RECORD_BYTES = 50;

    private StlMeshLoader() {
    }

    public static MeshData load(Path path) throws IOException {
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            return load(input);
        }
    }

    public static MeshData load(InputStream input) throws IOException {
        BufferedInputStream in = input instanceof BufferedInputStream buffered
            ? buffered
            : new BufferedInputStream(input);

        in.mark(BINARY_HEADER_BYTES + 4);
        byte[] prefix = in.readNBytes(BINARY_HEADER_BYTES + 4);
        in.reset();

        if (looksLikeBinaryStl(prefix)) {
            return loadBinary(in);
        }

        return load(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public static MeshData load(Reader reader) throws IOException {
        BufferedReader br = reader instanceof BufferedReader buffered ? buffered : new BufferedReader(reader);

        List<Float> positions = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        String line;
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
                indices.add(positions.size() / 3 - 1);
            }
        }

        return createMesh(toFloatArray(positions), toIntArray(indices));
    }

    private static MeshData loadBinary(InputStream input) throws IOException {
        byte[] header = input.readNBytes(BINARY_HEADER_BYTES);
        if (header.length != BINARY_HEADER_BYTES) {
            throw new EOFException("Unexpected EOF while reading STL header");
        }

        int triangleCount = readIntLE(input);
        if (triangleCount < 0) {
            throw new IOException("Invalid STL triangle count: " + triangleCount);
        }

        float[] positions = new float[Math.multiplyExact(triangleCount, 9)];
        int[] indices = new int[Math.multiplyExact(triangleCount, 3)];

        byte[] triRecord = new byte[BINARY_TRIANGLE_RECORD_BYTES];
        ByteBuffer bb = ByteBuffer.wrap(triRecord).order(ByteOrder.LITTLE_ENDIAN);

        int vertex = 0;
        for (int t = 0; t < triangleCount; t++) {
            int read = input.readNBytes(triRecord, 0, BINARY_TRIANGLE_RECORD_BYTES);
            if (read != BINARY_TRIANGLE_RECORD_BYTES) {
                throw new EOFException("Unexpected EOF while reading STL triangle data");
            }

            bb.position(12); // skip normal
            for (int i = 0; i < 3; i++) {
                positions[vertex * 3] = bb.getFloat();
                positions[vertex * 3 + 1] = bb.getFloat();
                positions[vertex * 3 + 2] = bb.getFloat();
                indices[vertex] = vertex;
                vertex++;
            }
            bb.getShort(); // attribute byte count
            bb.rewind();
        }

        return createMesh(positions, indices);
    }

    private static int readIntLE(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(4);
        if (bytes.length != 4) {
            throw new EOFException("Unexpected EOF while reading STL triangle count");
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static boolean looksLikeBinaryStl(byte[] prefix) {
        if (prefix.length < BINARY_HEADER_BYTES + 4) {
            return false;
        }

        String firstBytes = new String(prefix, 0, Math.min(5, prefix.length), StandardCharsets.US_ASCII);
        if (!"solid".equalsIgnoreCase(firstBytes)) {
            return true;
        }

        int triCount = ByteBuffer.wrap(prefix, BINARY_HEADER_BYTES, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
        return triCount > 0;
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
