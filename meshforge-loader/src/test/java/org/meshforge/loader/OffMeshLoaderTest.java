package org.meshforge.loader;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OffMeshLoaderTest {
    @Test
    void loadsOffAndTriangulatesPolygonFace() throws Exception {
        String off = """
            OFF
            4 1 0
            0 0 0
            1 0 0
            1 1 0
            0 1 0
            4 0 1 2 3
            """;

        var mesh = OffMeshLoader.load(new StringReader(off));
        assertEquals(4, mesh.vertexCount());
        assertNotNull(mesh.indicesOrNull());
        assertEquals(6, mesh.indicesOrNull().length);
    }
}
