package org.dynamisengine.meshforge.loader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void failsWhenFaceIndexIsOutOfBounds() {
        String off = """
            OFF
            3 1 0
            0 0 0
            1 0 0
            0 1 0
            3 0 1 3
            """;

        assertThrows(IOException.class, () -> OffMeshLoader.load(new StringReader(off)));
    }
}
