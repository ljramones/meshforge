package org.dynamisengine.meshforge.test;

import org.junit.jupiter.api.Test;
import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.core.builder.MeshWriter;
import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.MorphTarget;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.util.List;
import java.util.AbstractList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshStressFuzzTest {

    @Test
    void zeroTriangleMeshRunsCoreOpsWithoutUnexpectedFailures() {
        MeshData mesh = meshWithPosNormalUv(0, null, List.of());
        MeshData out = MeshPipeline.run(
            mesh,
            Ops.validate(),
            Ops.removeDegenerates(),
            Ops.normals(60f),
            Ops.tangents(),
            Ops.weld(1.0e-6f),
            Ops.optimizeVertexCache(),
            Ops.compactVertices(),
            Ops.bounds()
        );

        assertNotNull(out);
        assertEquals(0, out.vertexCount());
        assertTrue(out.has(AttributeSemantic.NORMAL, 0));
        assertTrue(out.has(AttributeSemantic.TANGENT, 0));
    }

    @Test
    void oneTriangleMeshRunsCoreOpsAndStaysSane() {
        MeshData mesh = meshWithPosNormalUv(
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m0"))
        );
        setPositions(mesh, new float[] {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        });
        setNormals(mesh, new float[] {
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f
        });
        setUvs(mesh, new float[] {
            0f, 0f,
            1f, 0f,
            0f, 1f
        });

        MeshData out = MeshPipeline.run(
            mesh,
            Ops.validate(),
            Ops.removeDegenerates(),
            Ops.normals(60f),
            Ops.tangents(),
            Ops.weld(1.0e-6f),
            Ops.optimizeVertexCache(),
            Ops.compactVertices(),
            Ops.bounds()
        );

        assertNotNull(out);
        assertEquals(3, out.vertexCount());
        assertNotNull(out.indicesOrNull());
        assertEquals(3, out.indicesOrNull().length);
        assertNotNull(out.boundsOrNull());
    }

    @Test
    void allDegenerateMeshDoesNotCorruptSubmeshRanges() {
        MeshData mesh = meshWithPosNormalUv(
            4,
            new int[] {0, 0, 0, 1, 1, 1},
            List.of(
                new Submesh(0, 0, "empty-head"),
                new Submesh(0, 6, "main"),
                new Submesh(6, 0, "empty-tail")
            )
        );
        setPositions(mesh, new float[] {
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f, 0f,
            0f, 0f, 0f
        });
        setNormals(mesh, new float[] {
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f,
            0f, 1f, 0f
        });
        setUvs(mesh, new float[] {
            0f, 0f,
            0f, 0f,
            0f, 0f,
            0f, 0f
        });

        MeshData out = MeshPipeline.run(
            mesh,
            Ops.removeDegenerates(),
            Ops.optimizeVertexCache(),
            Ops.compactVertices()
        );

        int[] indices = out.indicesOrNull();
        int indexCount = indices == null ? 0 : indices.length;
        for (Submesh submesh : out.submeshes()) {
            assertTrue(submesh.firstIndex() >= 0);
            assertTrue(submesh.indexCount() >= 0);
            assertTrue(submesh.firstIndex() + submesh.indexCount() <= indexCount);
            assertEquals(0, submesh.indexCount() % 3);
        }
    }

    @Test
    void morphTargetMismatchedDeltaLengthFailsFast() {
        MeshData mesh = new MeshData(positionSchema(), 2);
        MorphTarget bad = new MorphTarget("bad", new float[3], null, null);
        assertThrows(IllegalArgumentException.class, () -> mesh.addMorphTarget(bad));
    }

    @Test
    void packerRejectsNegativeIndices() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            3,
            new int[] {0, 1, -1},
            List.of(new Submesh(0, 3, "m0"))
        );
        setPositions(mesh, new float[] {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        });

        assertThrows(IllegalStateException.class, () -> MeshPacker.pack(mesh, PackSpec.realtimeMinimal()));
    }

    @Test
    void packerHandlesZeroVertexCountPositionOnly() {
        MeshData mesh = new MeshData(positionSchema(), 0);
        PackedMesh packed = MeshPacker.pack(mesh, PackSpec.realtimeMinimal());
        assertEquals(0, packed.vertexBuffer().capacity());
        assertEquals(16, packed.layout().strideBytes());
    }

    @Test
    void packerRejectsSpecWithoutPositionTarget() {
        MeshData mesh = new MeshData(positionSchema(), 1);
        mesh.attribute(AttributeSemantic.POSITION, 0).set3f(0, 0f, 0f, 0f);

        PackSpec badSpec = PackSpec.builder()
            .layout(PackSpec.LayoutMode.INTERLEAVED)
            .alignment(16)
            .indexPolicy(PackSpec.IndexPolicy.AUTO_16_IF_POSSIBLE)
            .build();

        assertThrows(IllegalStateException.class, () -> MeshPacker.pack(mesh, badSpec));
    }

    @Test
    void interleavedPositionOnlyPackRemainsValid() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m0"))
        );
        setPositions(mesh, new float[] {
            0f, 0f, 0f,
            1f, 0f, 0f,
            0f, 1f, 0f
        });

        PackedMesh packed = MeshPacker.pack(mesh, PackSpec.realtimeMinimal());
        assertEquals(16, packed.layout().strideBytes());
        assertNotNull(packed.layout().entry(new AttributeKey(AttributeSemantic.POSITION, 0)));
        assertNotNull(packed.indexBuffer());
        assertEquals(3, packed.indexBuffer().indexCount());
    }

    @Test
    void meshWriterRejectsExtremeVertexAndIndexCountsBeforeAllocation() {
        assertThrows(IllegalArgumentException.class, () -> new MeshWriter(positionSchema(), Integer.MAX_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> new MeshWriter(positionSchema(), 1, Integer.MAX_VALUE));
    }

    @Test
    void meshDataRejectsExtremeSubmeshCountBeforeCopy() {
        MeshData mesh = new MeshData(positionSchema(), 0);
        List<Submesh> huge = new AbstractList<>() {
            @Override
            public Submesh get(int index) {
                throw new AssertionError("get() must not be called when size exceeds limit");
            }

            @Override
            public int size() {
                return Integer.MAX_VALUE;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> mesh.setSubmeshes(huge));
    }

    private static VertexSchema positionSchema() {
        return VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();
    }

    private static MeshData meshWithPosNormalUv(int vertexCount, int[] indices, List<Submesh> submeshes) {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .build();
        return new MeshData(Topology.TRIANGLES, schema, vertexCount, indices, submeshes);
    }

    private static void setPositions(MeshData mesh, float[] values) {
        float[] pos = mesh.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        System.arraycopy(values, 0, pos, 0, Math.min(values.length, pos.length));
    }

    private static void setNormals(MeshData mesh, float[] values) {
        float[] nrm = mesh.attribute(AttributeSemantic.NORMAL, 0).rawFloatArrayOrNull();
        System.arraycopy(values, 0, nrm, 0, Math.min(values.length, nrm.length));
    }

    private static void setUvs(MeshData mesh, float[] values) {
        float[] uv = mesh.attribute(AttributeSemantic.UV, 0).rawFloatArrayOrNull();
        System.arraycopy(values, 0, uv, 0, Math.min(values.length, uv.length));
    }
}
