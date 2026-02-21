package org.meshforge.test;

import org.junit.jupiter.api.Test;
import org.meshforge.api.Meshes;
import org.meshforge.api.Ops;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.bounds.Boundsf;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;
import org.meshforge.ops.pipeline.MeshPipeline;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshPipelineOpsTest {

    @Test
    void validateFailsOnOutOfRangeIndex() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            3,
            new int[] {0, 1, 4},
            List.of(new Submesh(0, 3, "m"))
        );

        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0
        });

        assertThrows(IllegalStateException.class, () -> MeshPipeline.run(mesh, Ops.validate()));
    }

    @Test
    void computeBoundsForCube() {
        MeshData cube = Meshes.cube(1.0f);
        MeshData out = MeshPipeline.run(cube, Ops.bounds());

        Boundsf bounds = out.boundsOrNull();
        assertNotNull(bounds);
        assertEquals(-1.0f, bounds.aabb().minX(), 1.0e-6f);
        assertEquals(-1.0f, bounds.aabb().minY(), 1.0e-6f);
        assertEquals(-1.0f, bounds.aabb().minZ(), 1.0e-6f);
        assertEquals(1.0f, bounds.aabb().maxX(), 1.0e-6f);
        assertEquals(1.0f, bounds.aabb().maxY(), 1.0e-6f);
        assertEquals(1.0f, bounds.aabb().maxZ(), 1.0e-6f);
        assertEquals(0.0f, bounds.sphere().centerX(), 1.0e-6f);
        assertEquals(0.0f, bounds.sphere().centerY(), 1.0e-6f);
        assertEquals(0.0f, bounds.sphere().centerZ(), 1.0e-6f);
        assertEquals((float) Math.sqrt(3.0), bounds.sphere().radius(), 1.0e-5f);
    }

    @Test
    void recalculateNormalsProducesUnitNormals() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );

        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0
        });

        MeshData out = MeshPipeline.run(mesh, Ops.normals(60f));
        assertTrue(out.has(AttributeSemantic.NORMAL, 0));

        float[] n = out.attribute(AttributeSemantic.NORMAL, 0).rawFloatArrayOrNull();
        assertNotNull(n);
        for (int i = 0; i < out.vertexCount(); i++) {
            int o = i * 3;
            float len = (float) Math.sqrt(n[o] * n[o] + n[o + 1] * n[o + 1] + n[o + 2] * n[o + 2]);
            assertEquals(1.0f, len, 1.0e-5f);
            assertEquals(0.0f, n[o], 1.0e-5f);
            assertEquals(0.0f, n[o + 1], 1.0e-5f);
            assertTrue(n[o + 2] > 0.99f);
        }
    }

    @Test
    void recalculateTangentsProducesHandedness() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            4,
            new int[] {0, 1, 2, 0, 2, 3},
            List.of(new Submesh(0, 6, "m"))
        );

        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            1, 1, 0,
            0, 1, 0
        });

        float[] uv = {
            0, 0,
            1, 0,
            1, 1,
            0, 1
        };
        setUvs(mesh, uv);

        MeshData out = MeshPipeline.run(mesh, Ops.normals(60f), Ops.tangents());
        assertTrue(out.has(AttributeSemantic.TANGENT, 0));
        float[] t = out.attribute(AttributeSemantic.TANGENT, 0).rawFloatArrayOrNull();
        assertNotNull(t);

        for (int i = 0; i < out.vertexCount(); i++) {
            int o = i * 4;
            float len = (float) Math.sqrt(t[o] * t[o] + t[o + 1] * t[o + 1] + t[o + 2] * t[o + 2]);
            assertEquals(1.0f, len, 1.0e-4f);
            assertTrue(Math.abs(Math.abs(t[o + 3]) - 1.0f) < 1.0e-4f);
        }
    }

    @Test
    void weldAndCompactReduceVertexCount() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            4,
            new int[] {0, 1, 2, 3, 1, 2},
            List.of(new Submesh(0, 6, "m"))
        );

        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0,
            0, 0, 0
        });

        MeshData out = MeshPipeline.run(mesh, Ops.weld(1.0e-6f));
        assertEquals(3, out.vertexCount());
        assertNotNull(out.indicesOrNull());
        assertEquals(6, out.indicesOrNull().length);
    }

    @Test
    void compactPreservesGeneratedAttributes() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            4,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );

        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0,
            10, 10, 10
        });

        MeshData out = MeshPipeline.run(mesh, Ops.normals(60f), Ops.compactVertices());
        assertEquals(3, out.vertexCount());
        assertTrue(out.has(AttributeSemantic.NORMAL, 0));
        assertNotNull(out.attribute(AttributeSemantic.NORMAL, 0).rawFloatArrayOrNull());
    }

    private static VertexSchema positionSchema() {
        return VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();
    }

    private static void setPositions(MeshData mesh, float[] xyz) {
        var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        for (int i = 0; i < mesh.vertexCount(); i++) {
            int p = i * 3;
            pos.set3f(i, xyz[p], xyz[p + 1], xyz[p + 2]);
        }
    }

    private static void setUvs(MeshData mesh, float[] uv) {
        var uvView = mesh.attribute(AttributeSemantic.UV, 0);
        for (int i = 0; i < mesh.vertexCount(); i++) {
            int p = i * 2;
            uvView.set2f(i, uv[p], uv[p + 1]);
        }
    }
}
