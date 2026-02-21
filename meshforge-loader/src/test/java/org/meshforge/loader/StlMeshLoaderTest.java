package org.meshforge.loader;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

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
}
