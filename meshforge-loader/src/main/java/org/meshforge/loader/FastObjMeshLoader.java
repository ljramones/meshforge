package org.meshforge.loader;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Faster OBJ loader that parses memory-mapped bytes and avoids regex/string splitting.
 * v1 supports vertex positions and face indices only.
 */
public final class FastObjMeshLoader {
    private FastObjMeshLoader() {
    }

    public static MeshData load(Path path) throws IOException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
            long size = ch.size();
            if (size > Integer.MAX_VALUE) {
                throw new IOException("OBJ file too large for v1 loader: " + path);
            }
            MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, size);
            return parse(buf, (int) size);
        }
    }

    private static MeshData parse(MappedByteBuffer buf, int n) throws IOException {
        FloatArray positions = new FloatArray(8192);
        IntArray indices = new IntArray(8192);

        int i = 0;
        while (i < n) {
            i = skipHorizontalWhitespace(buf, i, n);
            if (i >= n) {
                break;
            }

            int b = byteAt(buf, i);
            if (b == '#') {
                i = skipLine(buf, i, n);
                continue;
            }
            if (isLineEnd(b)) {
                i++;
                continue;
            }

            if (b == 'v') {
                int next = i + 1 < n ? byteAt(buf, i + 1) : -1;
                if (isHorizontalWhitespace(next)) {
                    i = parseVertexLine(buf, i + 1, n, positions);
                    continue;
                }
                i = skipLine(buf, i, n);
                continue;
            }

            if (b == 'f') {
                int next = i + 1 < n ? byteAt(buf, i + 1) : -1;
                if (isHorizontalWhitespace(next)) {
                    i = parseFaceLine(buf, i + 1, n, indices);
                    continue;
                }
                i = skipLine(buf, i, n);
                continue;
            }

            i = skipLine(buf, i, n);
        }

        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        int vertexCount = positions.size / 3;
        int[] indexData = indices.toArray();
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            vertexCount,
            indexData,
            indexData.length == 0 ? List.of() : List.of(new Submesh(0, indexData.length, "default"))
        );

        float[] dst = mesh.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        if (dst == null) {
            throw new IOException("POSITION storage is unavailable");
        }
        System.arraycopy(positions.data, 0, dst, 0, positions.size);
        return mesh;
    }

    private static int parseVertexLine(MappedByteBuffer buf, int i, int n, FloatArray positions) throws IOException {
        i = skipHorizontalWhitespace(buf, i, n);
        float x = parseFloat(buf, i, n);
        i = skipNumber(buf, i, n);
        i = skipHorizontalWhitespace(buf, i, n);
        float y = parseFloat(buf, i, n);
        i = skipNumber(buf, i, n);
        i = skipHorizontalWhitespace(buf, i, n);
        float z = parseFloat(buf, i, n);
        i = skipNumber(buf, i, n);

        positions.add(x);
        positions.add(y);
        positions.add(z);
        return skipLine(buf, i, n);
    }

    private static int parseFaceLine(MappedByteBuffer buf, int i, int n, IntArray indices) throws IOException {
        i = skipHorizontalWhitespace(buf, i, n);
        int first = parseFaceVertexIndex(buf, i, n);
        i = skipFaceToken(buf, i, n);
        i = skipHorizontalWhitespace(buf, i, n);
        if (i >= n || isLineEnd(byteAt(buf, i))) {
            return skipLine(buf, i, n);
        }

        int prev = parseFaceVertexIndex(buf, i, n);
        i = skipFaceToken(buf, i, n);

        while (i < n) {
            i = skipHorizontalWhitespace(buf, i, n);
            if (i >= n || isLineEnd(byteAt(buf, i))) {
                break;
            }
            int curr = parseFaceVertexIndex(buf, i, n);
            i = skipFaceToken(buf, i, n);

            indices.add(first);
            indices.add(prev);
            indices.add(curr);
            prev = curr;
        }
        return skipLine(buf, i, n);
    }

    private static int parseFaceVertexIndex(MappedByteBuffer buf, int i, int n) throws IOException {
        int sign = 1;
        int b = byteAt(buf, i);
        if (b == '-') {
            sign = -1;
            i++;
        } else if (b == '+') {
            i++;
        }

        if (i >= n || !isDigit(byteAt(buf, i))) {
            throw new IOException("Invalid OBJ face token");
        }

        int value = 0;
        while (i < n) {
            b = byteAt(buf, i);
            if (!isDigit(b)) {
                break;
            }
            value = value * 10 + (b - '0');
            i++;
        }

        int idx = sign * value;
        if (idx <= 0) {
            throw new IOException("Only positive OBJ indices are supported in v1");
        }
        return idx - 1;
    }

    private static int skipFaceToken(MappedByteBuffer buf, int i, int n) {
        while (i < n) {
            int b = byteAt(buf, i);
            if (isHorizontalWhitespace(b) || isLineEnd(b)) {
                break;
            }
            i++;
        }
        return i;
    }

    private static int skipNumber(MappedByteBuffer buf, int i, int n) {
        while (i < n) {
            int b = byteAt(buf, i);
            if (isHorizontalWhitespace(b) || isLineEnd(b)) {
                break;
            }
            i++;
        }
        return i;
    }

    private static float parseFloat(MappedByteBuffer buf, int i, int n) throws IOException {
        if (i >= n) {
            throw new IOException("Unexpected end of line while parsing float");
        }

        int sign = 1;
        int b = byteAt(buf, i);
        if (b == '-') {
            sign = -1;
            i++;
        } else if (b == '+') {
            i++;
        }

        long intPart = 0;
        boolean sawDigits = false;
        while (i < n) {
            b = byteAt(buf, i);
            if (!isDigit(b)) {
                break;
            }
            intPart = intPart * 10L + (b - '0');
            i++;
            sawDigits = true;
        }

        long fracPart = 0;
        int fracDigits = 0;
        if (i < n && byteAt(buf, i) == '.') {
            i++;
            while (i < n) {
                b = byteAt(buf, i);
                if (!isDigit(b)) {
                    break;
                }
                fracPart = fracPart * 10L + (b - '0');
                fracDigits++;
                i++;
                sawDigits = true;
            }
        }

        if (!sawDigits) {
            throw new IOException("Invalid float token in OBJ");
        }

        int exp = 0;
        if (i < n) {
            b = byteAt(buf, i);
            if (b == 'e' || b == 'E') {
                i++;
                int expSign = 1;
                if (i < n) {
                    int eb = byteAt(buf, i);
                    if (eb == '-') {
                        expSign = -1;
                        i++;
                    } else if (eb == '+') {
                        i++;
                    }
                }

                if (i >= n || !isDigit(byteAt(buf, i))) {
                    throw new IOException("Invalid exponent in OBJ float");
                }
                int e = 0;
                while (i < n) {
                    int eb = byteAt(buf, i);
                    if (!isDigit(eb)) {
                        break;
                    }
                    e = e * 10 + (eb - '0');
                    i++;
                }
                exp = expSign * e;
            }
        }

        double value = intPart;
        if (fracDigits > 0) {
            value += fracPart / Math.pow(10.0, fracDigits);
        }
        if (exp != 0) {
            value *= Math.pow(10.0, exp);
        }
        return (float) (sign * value);
    }

    private static int skipHorizontalWhitespace(MappedByteBuffer buf, int i, int n) {
        while (i < n && isHorizontalWhitespace(byteAt(buf, i))) {
            i++;
        }
        return i;
    }

    private static int skipLine(MappedByteBuffer buf, int i, int n) {
        while (i < n) {
            int b = byteAt(buf, i++);
            if (b == '\n') {
                break;
            }
        }
        return i;
    }

    private static boolean isHorizontalWhitespace(int b) {
        return b == ' ' || b == '\t' || b == '\r';
    }

    private static boolean isLineEnd(int b) {
        return b == '\n' || b == '\r';
    }

    private static boolean isDigit(int b) {
        return b >= '0' && b <= '9';
    }

    private static int byteAt(MappedByteBuffer buf, int i) {
        return buf.get(i) & 0xFF;
    }

    private static final class FloatArray {
        float[] data;
        int size;

        FloatArray(int initial) {
            this.data = new float[Math.max(16, initial)];
        }

        void add(float v) {
            ensure(size + 1);
            data[size++] = v;
        }

        private void ensure(int needed) {
            if (needed <= data.length) {
                return;
            }
            int next = Math.max(needed, data.length * 2);
            float[] resized = new float[next];
            System.arraycopy(data, 0, resized, 0, size);
            data = resized;
        }
    }

    private static final class IntArray {
        int[] data;
        int size;

        IntArray(int initial) {
            this.data = new int[Math.max(16, initial)];
        }

        void add(int v) {
            ensure(size + 1);
            data[size++] = v;
        }

        int[] toArray() {
            int[] out = new int[size];
            System.arraycopy(data, 0, out, 0, size);
            return out;
        }

        private void ensure(int needed) {
            if (needed <= data.length) {
                return;
            }
            int next = Math.max(needed, data.length * 2);
            int[] resized = new int[next];
            System.arraycopy(data, 0, resized, 0, size);
            data = resized;
        }
    }
}
