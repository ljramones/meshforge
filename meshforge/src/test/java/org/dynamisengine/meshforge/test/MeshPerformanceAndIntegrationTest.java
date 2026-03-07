package org.dynamisengine.meshforge.test;

import org.junit.jupiter.api.Test;
import org.dynamisengine.meshforge.api.Ops;
import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;
import org.dynamisengine.meshforge.ops.optimize.CacheMetrics;
import org.dynamisengine.meshforge.ops.pipeline.MeshPipeline;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;
import org.dynamisengine.vectrix.gpu.Half;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MeshPerformanceAndIntegrationTest {

    @Test
    void optimizeVertexCacheImprovesAcmrOnShuffledGrid() {
        MeshData mesh = createPositionGrid(64, 64);
        int[] shuffled = mesh.indicesOrNull().clone();
        shuffleTriangles(shuffled, 123456789L);
        mesh.setIndices(shuffled);

        double before = CacheMetrics.acmr(shuffled, 32);
        double atvrBefore = CacheMetrics.atvr(shuffled, mesh.vertexCount(), 32);
        MeshData optimized = MeshPipeline.run(mesh, Ops.optimizeVertexCache());
        double after = CacheMetrics.acmr(optimized.indicesOrNull(), 32);
        double atvrAfter = CacheMetrics.atvr(optimized.indicesOrNull(), optimized.vertexCount(), 32);

        assertTrue(after < before, "Expected ACMR to decrease after optimization");
        assertTrue(atvrAfter < atvrBefore, "Expected ATVR to decrease after optimization");
    }

    @Test
    void optimizeVertexCacheCompletesWithinGuardrailBudget() {
        MeshData mesh = createPositionGrid(128, 128);
        int[] shuffled = mesh.indicesOrNull().clone();
        shuffleTriangles(shuffled, 987654321L);
        mesh.setIndices(shuffled);

        // Non-strict guardrail to catch severe regressions, not microbenchmark precision.
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> MeshPipeline.run(mesh, Ops.optimizeVertexCache()));
    }

    @Test
    void realtimePackedSizeIsSmallerThanDebugOnRichMesh() {
        MeshData mesh = createRichGrid(32, 32);

        PackedMesh debug = MeshPacker.pack(mesh, PackSpec.debug());
        PackedMesh realtime = MeshPacker.pack(mesh, PackSpec.realtime());

        int debugBytes = debug.vertexBuffer().capacity() + debug.indexBuffer().buffer().capacity();
        int realtimeBytes = realtime.vertexBuffer().capacity() + realtime.indexBuffer().buffer().capacity();

        assertTrue(realtime.vertexBuffer().capacity() < debug.vertexBuffer().capacity());
        assertTrue(realtimeBytes < debugBytes);
    }

    @Test
    void packedBufferCanBeDecodedViaLayoutForPositionAndUv() {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .build();

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            3,
            new int[] {0, 1, 2},
            List.of(new Submesh(0, 3, "m"))
        );

        var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        pos.set3f(0, -1.0f, 0.5f, 2.0f);
        pos.set3f(1, 0.0f, 0.0f, 0.0f);
        pos.set3f(2, 1.0f, 1.0f, 1.0f);

        var uv = mesh.attribute(AttributeSemantic.UV, 0);
        uv.set2f(0, 0.25f, 0.75f);
        uv.set2f(1, 0.0f, 0.0f);
        uv.set2f(2, 1.0f, 1.0f);

        PackedMesh packed = MeshPacker.pack(mesh, PackSpec.realtime());
        VertexLayout layout = packed.layout();
        ByteBuffer vb = packed.vertexBuffer();

        int stride = layout.strideBytes();
        int pOff = layout.entry(new AttributeKey(AttributeSemantic.POSITION, 0)).offsetBytes();
        int uvOff = layout.entry(new AttributeKey(AttributeSemantic.UV, 0)).offsetBytes();

        int base = 0 * stride;
        float x = vb.getFloat(base + pOff);
        float y = vb.getFloat(base + pOff + 4);
        float z = vb.getFloat(base + pOff + 8);

        short hu = vb.getShort(base + uvOff);
        short hv = vb.getShort(base + uvOff + 2);
        float u = Half.unpack(hu);
        float v = Half.unpack(hv);

        assertEquals(-1.0f, x, 1.0e-6f);
        assertEquals(0.5f, y, 1.0e-6f);
        assertEquals(2.0f, z, 1.0e-6f);
        assertEquals(0.25f, u, 5.0e-3f);
        assertEquals(0.75f, v, 5.0e-3f);
    }

    private static MeshData createPositionGrid(int cellsX, int cellsY) {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        int vx = cellsX + 1;
        int vy = cellsY + 1;
        int vertexCount = vx * vy;

        int[] indices = new int[cellsX * cellsY * 6];
        int w = 0;
        for (int y = 0; y < cellsY; y++) {
            for (int x = 0; x < cellsX; x++) {
                int v0 = y * vx + x;
                int v1 = v0 + 1;
                int v2 = v0 + vx;
                int v3 = v2 + 1;
                indices[w++] = v0;
                indices[w++] = v2;
                indices[w++] = v1;
                indices[w++] = v1;
                indices[w++] = v2;
                indices[w++] = v3;
            }
        }

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            vertexCount,
            indices,
            List.of(new Submesh(0, indices.length, "grid"))
        );

        var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        for (int y = 0; y < vy; y++) {
            for (int x = 0; x < vx; x++) {
                int i = y * vx + x;
                pos.set3f(i, x, y, 0.0f);
            }
        }
        return mesh;
    }

    private static MeshData createRichGrid(int cellsX, int cellsY) {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .add(AttributeSemantic.COLOR, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.JOINTS, 0, VertexFormat.I32x4)
            .add(AttributeSemantic.WEIGHTS, 0, VertexFormat.F32x4)
            .build();

        int vx = cellsX + 1;
        int vy = cellsY + 1;
        int vertexCount = vx * vy;

        int[] indices = new int[cellsX * cellsY * 6];
        int w = 0;
        for (int y = 0; y < cellsY; y++) {
            for (int x = 0; x < cellsX; x++) {
                int v0 = y * vx + x;
                int v1 = v0 + 1;
                int v2 = v0 + vx;
                int v3 = v2 + 1;
                indices[w++] = v0;
                indices[w++] = v2;
                indices[w++] = v1;
                indices[w++] = v1;
                indices[w++] = v2;
                indices[w++] = v3;
            }
        }

        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            vertexCount,
            indices,
            List.of(new Submesh(0, indices.length, "grid"))
        );

        var pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        var nrm = mesh.attribute(AttributeSemantic.NORMAL, 0);
        var tan = mesh.attribute(AttributeSemantic.TANGENT, 0);
        var uv = mesh.attribute(AttributeSemantic.UV, 0);
        var col = mesh.attribute(AttributeSemantic.COLOR, 0);
        var jnt = mesh.attribute(AttributeSemantic.JOINTS, 0);
        var wgt = mesh.attribute(AttributeSemantic.WEIGHTS, 0);

        for (int y = 0; y < vy; y++) {
            for (int x = 0; x < vx; x++) {
                int i = y * vx + x;
                pos.set3f(i, x, y, 0.0f);
                nrm.set3f(i, 0.0f, 0.0f, 1.0f);
                tan.set4f(i, 1.0f, 0.0f, 0.0f, 1.0f);
                uv.set2f(i, (float) x / cellsX, (float) y / cellsY);
                col.set4f(i, 1.0f, 0.5f, 0.25f, 1.0f);

                jnt.setInt(i, 0, 0);
                jnt.setInt(i, 1, 1);
                jnt.setInt(i, 2, 0);
                jnt.setInt(i, 3, 0);

                wgt.set4f(i, 0.75f, 0.25f, 0.0f, 0.0f);
            }
        }

        return mesh;
    }

    private static void shuffleTriangles(int[] indices, long seed) {
        int triCount = indices.length / 3;
        Random random = new Random(seed);
        for (int i = triCount - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            swapTri(indices, i, j);
        }
    }

    private static void swapTri(int[] indices, int a, int b) {
        int ao = a * 3;
        int bo = b * 3;
        for (int k = 0; k < 3; k++) {
            int tmp = indices[ao + k];
            indices[ao + k] = indices[bo + k];
            indices[bo + k] = tmp;
        }
    }

}
