package org.meshforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StlMeshLoaderTest {
    @Test
    void loadsAsciiStlTriangles() throws Exception {
        String stl = """
            solid test
              facet normal 0 0 1
                outer loop
                  vertex 0 0 0
                  vertex 1 0 0
                  vertex 0 1 0
                endloop
              endfacet
            endsolid test
            """;

        var mesh = StlMeshLoader.load(new StringReader(stl));
        assertEquals(3, mesh.vertexCount());
        assertNotNull(mesh.indicesOrNull());
        assertEquals(3, mesh.indicesOrNull().length);
    }

    @Test
    void loadsBinaryStlTriangles(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("triangle.stl");
        Files.write(file, binaryTriangleStl());

        var mesh = StlMeshLoader.load(file);
        assertEquals(3, mesh.vertexCount());
        assertNotNull(mesh.indicesOrNull());
        assertEquals(3, mesh.indicesOrNull().length);
    }

    private static byte[] binaryTriangleStl() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("meshforge-binary-stl".getBytes(StandardCharsets.US_ASCII));
        while (out.size() < 80) {
            out.write(0);
        }

        ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(1);
        out.writeBytes(header.array());

        ByteBuffer tri = ByteBuffer.allocate(50).order(ByteOrder.LITTLE_ENDIAN);
        tri.putFloat(0f).putFloat(0f).putFloat(1f); // normal
        tri.putFloat(0f).putFloat(0f).putFloat(0f); // v0
        tri.putFloat(1f).putFloat(0f).putFloat(0f); // v1
        tri.putFloat(0f).putFloat(1f).putFloat(0f); // v2
        tri.putShort((short) 0);
        out.writeBytes(tri.array());

        return out.toByteArray();
    }
}
