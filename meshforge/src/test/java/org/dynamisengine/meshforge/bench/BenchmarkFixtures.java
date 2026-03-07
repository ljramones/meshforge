package org.dynamisengine.meshforge.bench;

import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class BenchmarkFixtures {
    private BenchmarkFixtures() {
    }

    static MeshData createPositionGrid(int cellsX, int cellsY) {
        VertexSchema schema = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .build();

        int vx = cellsX + 1;
        int vy = cellsY + 1;
        int vertexCount = vx * vy;

        int[] indices = makeGridIndices(cellsX, cellsY);
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            vertexCount,
            indices,
            List.of(new Submesh(0, indices.length, "grid"))
        );

        VertexAttributeView pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        for (int y = 0; y < vy; y++) {
            for (int x = 0; x < vx; x++) {
                int i = y * vx + x;
                pos.set3f(i, x, y, 0.0f);
            }
        }
        return mesh;
    }

    static MeshData createPositionGridWithDegenerates(int cellsX, int cellsY, int everyNthTriangle) {
        MeshData mesh = createPositionGrid(cellsX, cellsY);
        int[] indices = mesh.indicesOrNull();
        if (indices == null || everyNthTriangle <= 0) {
            return mesh;
        }
        int triCount = indices.length / 3;
        for (int t = 0; t < triCount; t++) {
            if (t % everyNthTriangle == 0) {
                int base = t * 3;
                indices[base + 2] = indices[base];
            }
        }
        mesh.setIndices(indices);
        return mesh;
    }

    static MeshData createRichGrid(int cellsX, int cellsY) {
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

        int[] indices = makeGridIndices(cellsX, cellsY);
        MeshData mesh = new MeshData(
            Topology.TRIANGLES,
            schema,
            vertexCount,
            indices,
            List.of(new Submesh(0, indices.length, "grid"))
        );

        VertexAttributeView pos = mesh.attribute(AttributeSemantic.POSITION, 0);
        VertexAttributeView nrm = mesh.attribute(AttributeSemantic.NORMAL, 0);
        VertexAttributeView tan = mesh.attribute(AttributeSemantic.TANGENT, 0);
        VertexAttributeView uv = mesh.attribute(AttributeSemantic.UV, 0);
        VertexAttributeView col = mesh.attribute(AttributeSemantic.COLOR, 0);
        VertexAttributeView jnt = mesh.attribute(AttributeSemantic.JOINTS, 0);
        VertexAttributeView wgt = mesh.attribute(AttributeSemantic.WEIGHTS, 0);

        for (int y = 0; y < vy; y++) {
            for (int x = 0; x < vx; x++) {
                int i = y * vx + x;
                pos.set3f(i, x, y, (x ^ y) * 0.001f);
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

    static MeshData copyOf(MeshData src) {
        int[] indices = src.indicesOrNull();
        List<Submesh> copiedSubmeshes = new ArrayList<>(src.submeshes());
        MeshData dst = new MeshData(
            src.topology(),
            src.schema(),
            src.vertexCount(),
            indices == null ? null : indices.clone(),
            copiedSubmeshes
        );

        for (Map.Entry<AttributeKey, VertexFormat> entry : src.attributeFormats().entrySet()) {
            AttributeKey key = entry.getKey();
            VertexFormat format = entry.getValue();
            VertexAttributeView in = src.attribute(key.semantic(), key.setIndex());
            VertexAttributeView out = dst.attribute(key.semantic(), key.setIndex());
            copyAttribute(in, out, format);
        }

        dst.setBounds(src.boundsOrNull());
        return dst;
    }

    static void shuffleTriangles(int[] indices, long seed) {
        int triCount = indices.length / 3;
        Random random = new Random(seed);
        for (int i = triCount - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            swapTri(indices, i, j);
        }
    }

    private static void copyAttribute(VertexAttributeView in, VertexAttributeView out, VertexFormat format) {
        int vc = in.vertexCount();
        int comps = format.components();
        switch (format.kind()) {
            case FLOAT -> {
                for (int i = 0; i < vc; i++) {
                    for (int c = 0; c < comps; c++) {
                        out.setFloat(i, c, in.getFloat(i, c));
                    }
                }
            }
            case INT, SHORT, BYTE -> {
                for (int i = 0; i < vc; i++) {
                    for (int c = 0; c < comps; c++) {
                        out.setInt(i, c, in.getInt(i, c));
                    }
                }
            }
        }
    }

    private static int[] makeGridIndices(int cellsX, int cellsY) {
        int vx = cellsX + 1;
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
        return indices;
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
