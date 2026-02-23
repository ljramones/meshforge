package org.meshforge.loader;

import org.junit.jupiter.api.Test;
import org.meshforge.core.attr.AttributeSemantic;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjMeshLoaderTest {
    @Test
    void loadsPositionsAndTriangulatesQuadFace() throws Exception {
        String obj = """
            v 0 0 0
            v 1 0 0
            v 1 1 0
            v 0 1 0
            f 1 2 3 4
            """;

        var mesh = ObjMeshLoader.load(new StringReader(obj));
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

    @Test
    void failsWhenFaceIndexIsOutOfBounds() {
        String obj = """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 4
            """;

        assertThrows(IOException.class, () -> ObjMeshLoader.load(new StringReader(obj)));
    }
}
