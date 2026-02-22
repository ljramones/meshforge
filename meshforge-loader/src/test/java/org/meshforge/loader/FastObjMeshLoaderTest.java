package org.meshforge.loader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.meshforge.core.attr.AttributeSemantic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FastObjMeshLoaderTest {
    @Test
    void loadsPositionsAndTriangulatesQuadFace(@TempDir Path tempDir) throws Exception {
        String obj = """
            # quad
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            f 1/7/2 2/7/2 3/7/2 4/7/2
            """;
        Path file = tempDir.resolve("quad.obj");
        Files.writeString(file, obj, StandardCharsets.US_ASCII);

        var mesh = FastObjMeshLoader.load(file);
        assertEquals(4, mesh.vertexCount());
        assertNotNull(mesh.indicesOrNull());
        assertEquals(6, mesh.indicesOrNull().length);
        assertEquals(0, mesh.indicesOrNull()[0]);
        assertEquals(1, mesh.indicesOrNull()[1]);
        assertEquals(2, mesh.indicesOrNull()[2]);
        assertEquals(0, mesh.indicesOrNull()[3]);
        assertEquals(2, mesh.indicesOrNull()[4]);
        assertEquals(3, mesh.indicesOrNull()[5]);

        var pos = mesh.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        assertNotNull(pos);
        assertEquals(12, pos.length);
    }
}
