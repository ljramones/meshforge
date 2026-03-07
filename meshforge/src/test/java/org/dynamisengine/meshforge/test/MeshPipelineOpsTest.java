package org.dynamisengine.meshforge.test;

import org.junit.jupiter.api.Test;
import org.dynamisengine.meshforge.api.Meshes;
import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.bounds.Boundsf;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.ops.optimize.MeshletClusters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    void recalculateNormalsRespectsAngleThreshold() {
        MeshData base = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            4,
            new int[] {0, 1, 2, 0, 3, 1},
            List.of(new Submesh(0, 6, "m"))
        );
        setPositions(base, new float[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0,
            0, 0, 1
        });

        MeshData smooth = MeshPipeline.run(copyMesh(base), Ops.normals(180f));
        MeshData hard = MeshPipeline.run(copyMesh(base), Ops.normals(10f));

        float[] smoothN = smooth.attribute(AttributeSemantic.NORMAL, 0).rawFloatArrayOrNull();
        float[] hardN = hard.attribute(AttributeSemantic.NORMAL, 0).rawFloatArrayOrNull();
        assertNotNull(smoothN);
        assertNotNull(hardN);

        int o = 0;
        // Smooth case blends +Y and +Z normals; hard case keeps the dominant cluster.
        assertTrue(smoothN[o + 1] > 0.6f && smoothN[o + 2] > 0.6f);
        assertTrue(hardN[o + 2] > 0.99f);
        assertTrue(Math.abs(hardN[o + 1]) < 1.0e-4f);
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
    void validateFailsOnNonOrthogonalTangent() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .build();
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );
        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0
        });
        for (int i = 0; i < 3; i++) {
            mesh.attribute(AttributeSemantic.NORMAL, 0).set3f(i, 0, 0, 1);
            mesh.attribute(AttributeSemantic.TANGENT, 0).set4f(i, 0, 0, 1, 1);
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> MeshPipeline.run(mesh, Ops.validate()));
        assertTrue(ex.getMessage().contains("not orthogonal"));
    }

    @Test
    void validateFailsOnInvalidTangentHandedness() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .build();
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );
        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0
        });
        for (int i = 0; i < 3; i++) {
            mesh.attribute(AttributeSemantic.NORMAL, 0).set3f(i, 0, 0, 1);
            mesh.attribute(AttributeSemantic.TANGENT, 0).set4f(i, 1, 0, 0, 0);
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> MeshPipeline.run(mesh, Ops.validate()));
        assertTrue(ex.getMessage().contains("handedness"));
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
    void weldMergesVerticesAcrossQuantizationBoundary() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            4,
            new int[] {0, 1, 2, 3, 1, 2},
            List.of(new Submesh(0, 6, "m"))
        );
        setPositions(mesh, new float[] {
            0.49e-6f, 0, 0,
            1, 0, 0,
            0, 1, 0,
            0.51e-6f, 0, 0
        });

        MeshData out = MeshPipeline.run(mesh, Ops.weld(1.0e-6f));
        assertEquals(3, out.vertexCount());
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

    @Test
    void ensureTrianglesNoOpForIndexedTriangles() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );

        MeshData out = MeshPipeline.run(mesh, Ops.triangulate());
        assertSame(mesh, out);
        assertArrayEquals(new int[] {0, 1, 2}, out.indicesOrNull());
        assertEquals(1, out.submeshes().size());
        assertEquals(3, out.submeshes().get(0).indexCount());
    }

    @Test
    void ensureTrianglesBuildsSequentialIndicesForTriangleSoup() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            6,
            null,
            List.of()
        );

        MeshData out = MeshPipeline.run(mesh, Ops.ensureTriangles());
        assertNotNull(out.indicesOrNull());
        assertArrayEquals(new int[] {0, 1, 2, 3, 4, 5}, out.indicesOrNull());
        assertEquals(1, out.submeshes().size());
        assertEquals(0, out.submeshes().get(0).firstIndex());
        assertEquals(6, out.submeshes().get(0).indexCount());
    }

    @Test
    void ensureTrianglesFailsWhenTriangleSoupVertexCountIsInvalid() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            4,
            null,
            List.of()
        );

        assertThrows(IllegalStateException.class, () -> MeshPipeline.run(mesh, Ops.ensureTriangles()));
    }

    @Test
    void ensureTrianglesFailsForNonTriangleTopology() {
        MeshData mesh = new MeshData(
            Topology.LINES,
            positionSchema(),
            2,
            new int[] {0, 1},
            List.of(new Submesh(0, 2, "m"))
        );

        assertThrows(UnsupportedOperationException.class, () -> MeshPipeline.run(mesh, Ops.triangulate()));
    }

    @Test
    void clusterizeMeshletsReordersDeterministicallyAndPreservesTriangleCount() {
        MeshData a = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            8,
            new int[] {
                0, 1, 2,
                2, 3, 0,
                4, 5, 6,
                6, 7, 4
            },
            List.of(new Submesh(0, 12, "m"))
        );
        MeshData b = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            8,
            new int[] {
                0, 1, 2,
                2, 3, 0,
                4, 5, 6,
                6, 7, 4
            },
            List.of(new Submesh(0, 12, "m"))
        );

        MeshData outA = MeshPipeline.run(a, Ops.clusterizeMeshlets(4, 2));
        MeshData outB = MeshPipeline.run(b, Ops.clusterizeMeshlets(4, 2));

        assertNotNull(outA.indicesOrNull());
        assertNotNull(outB.indicesOrNull());
        assertEquals(12, outA.indicesOrNull().length);
        assertArrayEquals(outA.indicesOrNull(), outB.indicesOrNull());
    }

    @Test
    void optimizeMeshletOrderIsDeterministicAndImprovesLocality() {
        MeshData a = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            12,
            new int[] {
                0, 1, 2,   // cluster A
                6, 7, 8,   // cluster C
                3, 4, 5,   // cluster B
                9, 10, 11  // cluster D
            },
            List.of(new Submesh(0, 12, "m"))
        );
        MeshData b = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            12,
            new int[] {
                0, 1, 2,
                6, 7, 8,
                3, 4, 5,
                9, 10, 11
            },
            List.of(new Submesh(0, 12, "m"))
        );
        // Four spatial clusters in intentionally scrambled triangle order.
        setPositions(a, new float[] {
            0, 0, 0,   0.5f, 0, 0,   0, 0.5f, 0,   // A
            10, 0, 0,  10.5f, 0, 0, 10, 0.5f, 0,   // B
            0, 10, 0,  0.5f, 10, 0, 0, 10.5f, 0,   // C
            10, 10, 0, 10.5f, 10, 0, 10, 10.5f, 0  // D
        });
        setPositions(b, new float[] {
            0, 0, 0,   0.5f, 0, 0,   0, 0.5f, 0,
            10, 0, 0,  10.5f, 0, 0, 10, 0.5f, 0,
            0, 10, 0,  0.5f, 10, 0, 0, 10.5f, 0,
            10, 10, 0, 10.5f, 10, 0, 10, 10.5f, 0
        });

        double before = MeshletClusters.averageMeshletCenterStep(
            MeshletClusters.buildMeshlets(a, a.indicesOrNull(), 3, 1)
        );
        MeshData outA = MeshPipeline.run(a, Ops.optimizeMeshletOrder(3, 1));
        MeshData outB = MeshPipeline.run(b, Ops.optimizeMeshletOrder(3, 1));
        double after = MeshletClusters.averageMeshletCenterStep(
            MeshletClusters.buildMeshlets(outA, outA.indicesOrNull(), 3, 1)
        );

        assertArrayEquals(outA.indicesOrNull(), outB.indicesOrNull());
        assertTrue(after <= before, "expected optimized meshlet order to not worsen locality");
    }

    @Test
    void removeDegeneratesPreservesMaterialPartitions() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            6,
            new int[] {
                0, 0, 1,
                3, 4, 5
            },
            List.of(
                new Submesh(0, 3, "mat-a"),
                new Submesh(3, 3, "mat-b")
            )
        );
        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            2, 0, 0,
            10, 0, 0,
            10, 1, 0,
            10, 0, 1
        });

        MeshData out = MeshPipeline.run(mesh, Ops.removeDegenerates());

        assertArrayEquals(new int[] {3, 4, 5}, out.indicesOrNull());
        assertEquals(1, out.submeshes().size());
        assertEquals("mat-b", out.submeshes().get(0).materialId());
        assertEquals(0, out.submeshes().get(0).firstIndex());
        assertEquals(3, out.submeshes().get(0).indexCount());
    }

    @Test
    void clusterizeMeshletsPreservesMaterialTriangleSets() {
        MeshData mesh = twoMaterialFixture();
        Map<Object, List<String>> expected = signaturesByMaterial(mesh);

        MeshData out = MeshPipeline.run(mesh, Ops.clusterizeMeshlets(4, 2));

        assertEquals(expected, signaturesByMaterial(out));
    }

    @Test
    void optimizeMeshletOrderPreservesMaterialTriangleSets() {
        MeshData mesh = twoMaterialFixture();
        Map<Object, List<String>> expected = signaturesByMaterial(mesh);

        MeshData out = MeshPipeline.run(mesh, Ops.optimizeMeshletOrder(4, 2));

        assertEquals(expected, signaturesByMaterial(out));
    }

    @Test
    void optimizeVertexCachePreservesMaterialTriangleSets() {
        MeshData mesh = twoMaterialFixture();
        Map<Object, List<String>> expected = signaturesByMaterial(mesh);

        MeshData out = MeshPipeline.run(mesh, Ops.optimizeVertexCache());

        assertEquals(expected, signaturesByMaterial(out));
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

    private static MeshData twoMaterialFixture() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            12,
            new int[] {
                0, 1, 2,
                2, 3, 0,
                8, 9, 10,
                10, 11, 8
            },
            List.of(
                new Submesh(0, 6, "mat-a"),
                new Submesh(6, 6, "mat-b")
            )
        );
        setPositions(mesh, new float[] {
            0, 0, 0,   1, 0, 0,   1, 1, 0,   0, 1, 0,
            0, 0, 0,   0, 0, 0,   0, 0, 0,   0, 0, 0,
            10, 0, 0, 11, 0, 0, 11, 1, 0, 10, 1, 0
        });
        return mesh;
    }

    private static MeshData copyMesh(MeshData src) {
        MeshData out = new MeshData(
            src.topology(),
            src.schema(),
            src.vertexCount(),
            src.indicesOrNull(),
            src.submeshes()
        );
        float[] srcPos = src.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        float[] dstPos = out.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        assertNotNull(srcPos);
        assertNotNull(dstPos);
        System.arraycopy(srcPos, 0, dstPos, 0, srcPos.length);
        return out;
    }

    private static Map<Object, List<String>> signaturesByMaterial(MeshData mesh) {
        int[] indices = mesh.indicesOrNull();
        assertNotNull(indices);
        float[] pos = mesh.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        assertNotNull(pos);

        Map<Object, List<String>> out = new HashMap<>();
        for (Submesh submesh : mesh.submeshes()) {
            List<String> signatures = out.computeIfAbsent(submesh.materialId(), ignored -> new ArrayList<>());
            int first = submesh.firstIndex();
            int end = first + submesh.indexCount();
            for (int i = first; i < end; i += 3) {
                signatures.add(triangleSignature(indices[i], indices[i + 1], indices[i + 2], pos));
            }
            signatures.sort(String::compareTo);
        }
        return out;
    }

    private static String triangleSignature(int ia, int ib, int ic, float[] pos) {
        String[] v = new String[] {
            vertexSignature(ia, pos),
            vertexSignature(ib, pos),
            vertexSignature(ic, pos)
        };
        java.util.Arrays.sort(v);
        return v[0] + "|" + v[1] + "|" + v[2];
    }

    private static String vertexSignature(int i, float[] pos) {
        int p = i * 3;
        return Float.floatToIntBits(pos[p]) + "," + Float.floatToIntBits(pos[p + 1]) + "," + Float.floatToIntBits(pos[p + 2]);
    }
}
