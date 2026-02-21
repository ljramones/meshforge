package org.meshforge.loader;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlyMeshLoaderTest {
    @Test
    void loadsAsciiPlyAndTriangulatesFace() throws Exception {
        String ply = """
            ply
            format ascii 1.0
            element vertex 4
            property float x
            property float y
            property float z
            element face 1
            property list uchar int vertex_indices
            end_header
            0 0 0
            1 0 0
            1 1 0
            0 1 0
            4 0 1 2 3
            """;

        var mesh = PlyMeshLoader.load(new StringReader(ply));
        assertEquals(4, mesh.vertexCount());
        assertNotNull(mesh.indicesOrNull());
        assertEquals(6, mesh.indicesOrNull().length);
    }
}
