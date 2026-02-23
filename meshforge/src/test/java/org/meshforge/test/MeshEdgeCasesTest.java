package org.meshforge.test;

import org.junit.jupiter.api.Test;
import org.meshforge.api.Ops;
import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;
import org.meshforge.ops.pipeline.MeshPipeline;
import org.meshforge.ops.optimize.OptimizeVertexCacheOp;
import org.meshforge.pack.buffer.PackedMesh;
import org.meshforge.pack.packer.MeshPacker;
import org.meshforge.pack.spec.PackSpec;
import org.vectrix.gpu.Half;
import org.vectrix.gpu.PackedNorm;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshEdgeCasesTest {

    @Test
    void removeDegeneratesRemovesZeroAreaTriangles() {
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
            2, 0, 0
        });

        MeshData out = MeshPipeline.run(mesh, Ops.removeDegenerates());
        assertNotNull(out.indicesOrNull());
        assertEquals(0, out.indicesOrNull().length);
    }

    @Test
    void optimizeVertexCachePreservesTriangleSet() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            5,
            new int[] {0, 1, 2, 2, 3, 4, 0, 2, 4},
            List.of(new Submesh(0, 9, "m"))
        );

        int beforeLength = mesh.indicesOrNull().length;
        MeshData out = MeshPipeline.run(mesh, Ops.optimizeVertexCache());
        int[] after = out.indicesOrNull();

        assertEquals(beforeLength, after.length);
        for (int idx : after) {
            assertTrue(idx >= 0 && idx < out.vertexCount());
        }
    }

    @Test
    void optimizeProducesContiguousRemapAndIndicesInRange() {
        int[] indices = {5, 7, 9, 9, 7, 5, 7, 9, 5};
        OptimizeVertexCacheOp.Result result = OptimizeVertexCacheOp.optimize(indices, 12, 32);

        int max = -1;
        for (int idx : result.indices()) {
            assertTrue(idx >= 0);
            max = Math.max(max, idx);
        }
        assertEquals(result.vertexCount() - 1, max);

        int seen = 0;
        for (int m : result.vertexRemap()) {
            if (m >= 0) {
                seen++;
            }
        }
        assertEquals(result.vertexCount(), seen);
    }

    @Test
    void weldCurrentlyMergesCoincidentPositionsAcrossUvSeams() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            4,
            new int[] {0, 2, 3, 1, 2, 3},
            List.of(new Submesh(0, 6, "m"))
        );

        setPositions(mesh, new float[] {
            0, 0, 0,
            0, 0, 0,
            1, 0, 0,
            0, 1, 0
        });

        var uv = mesh.attribute(AttributeSemantic.UV, 0);
        uv.set2f(0, 0.0f, 0.0f);
        uv.set2f(1, 1.0f, 1.0f);
        uv.set2f(2, 1.0f, 0.0f);
        uv.set2f(3, 0.0f, 1.0f);

        MeshData out = MeshPipeline.run(mesh, Ops.weld(1.0e-6f));
        assertEquals(3, out.vertexCount());
    }

    @Test
    void packerWritesExpectedPackedValues() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            1,
            new int[] {0, 0, 0},
            List.of(new Submesh(0, 3, "m"))
        );

        mesh.attribute(AttributeSemantic.POSITION, 0).set3f(0, 1.0f, 2.0f, 3.0f);
        mesh.attribute(AttributeSemantic.NORMAL, 0).set3f(0, 0.0f, 0.0f, 1.0f);
        mesh.attribute(AttributeSemantic.TANGENT, 0).set4f(0, 1.0f, 0.0f, 0.0f, 1.0f);
        mesh.attribute(AttributeSemantic.UV, 0).set2f(0, 0.5f, 0.25f);

        PackedMesh packed = MeshPacker.pack(mesh, PackSpec.realtime());
        var layout = packed.layout();
        var vb = packed.vertexBuffer();

        int pOff = layout.entry(new AttributeKey(AttributeSemantic.POSITION, 0)).offsetBytes();
        int nOff = layout.entry(new AttributeKey(AttributeSemantic.NORMAL, 0)).offsetBytes();
        int tOff = layout.entry(new AttributeKey(AttributeSemantic.TANGENT, 0)).offsetBytes();
        int uvOff = layout.entry(new AttributeKey(AttributeSemantic.UV, 0)).offsetBytes();

        assertEquals(1.0f, vb.getFloat(pOff), 1.0e-6f);
        assertEquals(2.0f, vb.getFloat(pOff + 4), 1.0e-6f);
        assertEquals(3.0f, vb.getFloat(pOff + 8), 1.0e-6f);

        assertEquals(PackedNorm.packSnorm8x4(0.0f, 0.0f, 1.0f, 0.0f), vb.getInt(nOff));
        assertEquals(PackedNorm.packSnorm8x4(1.0f, 0.0f, 0.0f, 1.0f), vb.getInt(tOff));
        assertEquals(Half.pack(0.5f), vb.getShort(uvOff));
        assertEquals(Half.pack(0.25f), vb.getShort(uvOff + 2));
    }

    @Test
    void addAttributeRejectsDuplicates() {
        MeshData mesh = new MeshData(positionSchema(), 1);
        assertThrows(IllegalStateException.class,
            () -> mesh.addAttribute(AttributeSemantic.POSITION, 0, VertexFormat.F32x3));
    }

    @Test
    void addAttributeUpdatesSchema() {
        MeshData mesh = new MeshData(positionSchema(), 1);
        assertFalse(mesh.schema().has(AttributeSemantic.NORMAL, 0));
        mesh.addAttribute(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3);
        assertTrue(mesh.schema().has(AttributeSemantic.NORMAL, 0));
    }

    @Test
    void setVertexCountRejectsResizing() {
        MeshData mesh = new MeshData(positionSchema(), 1);
        assertThrows(UnsupportedOperationException.class, () -> mesh.setVertexCount(2));
        mesh.setVertexCount(1);
    }

    @Test
    void validateFailsForMalformedSubmeshRange() {
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            positionSchema(),
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(1, 3, "m"))
        );
        setPositions(mesh, new float[] {
            0, 0, 0,
            1, 0, 0,
            0, 1, 0
        });

        assertThrows(IllegalStateException.class, () -> MeshPipeline.run(mesh, Ops.validate()));
    }

    @Test
    void nonIndexedAndEmptyMeshesPack() {
        MeshData nonIndexed = new MeshData(positionSchema(), 2);
        setPositions(nonIndexed, new float[] {0, 0, 0, 1, 0, 0});
        PackedMesh packedNonIndexed = MeshPacker.pack(nonIndexed, PackSpec.realtime());
        assertNull(packedNonIndexed.indexBuffer());
        assertEquals(2 * packedNonIndexed.layout().strideBytes(), packedNonIndexed.vertexBuffer().capacity());

        MeshData empty = new MeshData(positionSchema(), 0);
        PackedMesh packedEmpty = MeshPacker.pack(empty, PackSpec.realtime());
        assertEquals(0, packedEmpty.vertexBuffer().capacity());
    }

    private static VertexSchema positionSchema() {
        return VertexSchema.builder().add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3).build();
    }

    private static void setPositions(MeshData mesh, float[] xyz) {
        var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        for (int i = 0; i < mesh.vertexCount(); i++) {
            int p = i * 3;
            pos.set3f(i, xyz[p], xyz[p + 1], xyz[p + 2]);
        }
    }

    private static int[] sortedTriangles(int[] indices) {
        int triCount = indices.length / 3;
        int[] norm = new int[indices.length];
        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3];
            int b = indices[t * 3 + 1];
            int c = indices[t * 3 + 2];
            int[] tri = new int[] {a, b, c};
            Arrays.sort(tri);
            norm[t * 3] = tri[0];
            norm[t * 3 + 1] = tri[1];
            norm[t * 3 + 2] = tri[2];
        }
        for (int i = 0; i < triCount; i++) {
            for (int j = i + 1; j < triCount; j++) {
                int io = i * 3;
                int jo = j * 3;
                if (compareTri(norm, jo, io) < 0) {
                    swapTri(norm, io, jo);
                }
            }
        }
        return norm;
    }

    private static int compareTri(int[] arr, int aOff, int bOff) {
        for (int k = 0; k < 3; k++) {
            int d = Integer.compare(arr[aOff + k], arr[bOff + k]);
            if (d != 0) {
                return d;
            }
        }
        return 0;
    }

    private static void swapTri(int[] arr, int aOff, int bOff) {
        for (int k = 0; k < 3; k++) {
            int tmp = arr[aOff + k];
            arr[aOff + k] = arr[bOff + k];
            arr[bOff + k] = tmp;
        }
    }
}
