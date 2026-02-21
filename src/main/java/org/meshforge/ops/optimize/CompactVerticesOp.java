package org.meshforge.ops.optimize;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;

import java.util.Arrays;
import java.util.Map;

/**
 * Removes unreferenced vertices and rewrites indices/attributes.
 */
public final class CompactVerticesOp implements MeshOp {
    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length == 0) {
            return mesh;
        }

        int vertexCount = mesh.vertexCount();
        boolean[] used = new boolean[vertexCount];
        for (int idx : indices) {
            if (idx >= 0 && idx < vertexCount) {
                used[idx] = true;
            }
        }

        int[] oldToNew = new int[vertexCount];
        Arrays.fill(oldToNew, -1);
        int newVertexCount = 0;
        for (int v = 0; v < vertexCount; v++) {
            if (used[v]) {
                oldToNew[v] = newVertexCount++;
            }
        }

        if (newVertexCount == vertexCount) {
            return mesh;
        }

        int[] remappedIndices = new int[indices.length];
        for (int i = 0; i < indices.length; i++) {
            remappedIndices[i] = oldToNew[indices[i]];
        }

        MeshData compacted = new MeshData(
            mesh.topology(),
            mesh.schema(),
            newVertexCount,
            remappedIndices,
            mesh.submeshes()
        );

        copyAttributes(mesh, compacted, oldToNew, newVertexCount);
        compacted.setBounds(mesh.boundsOrNull());
        return compacted;
    }

    public static MeshData remap(MeshData mesh, int[] remappedIndices) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length != remappedIndices.length) {
            throw new IllegalArgumentException("remappedIndices must match current index count");
        }

        MeshData remapped = new MeshData(
            mesh.topology(),
            mesh.schema(),
            mesh.vertexCount(),
            remappedIndices,
            mesh.submeshes()
        );

        int[] identity = new int[mesh.vertexCount()];
        for (int i = 0; i < identity.length; i++) {
            identity[i] = i;
        }
        copyAttributes(mesh, remapped, identity, mesh.vertexCount());
        remapped.setBounds(mesh.boundsOrNull());
        return remapped;
    }

    private static void copyAttributes(MeshData source, MeshData target, int[] oldToNew, int targetVertexCount) {
        for (Map.Entry<AttributeKey, VertexFormat> entry : source.attributeFormats().entrySet()) {
            AttributeKey key = entry.getKey();
            VertexFormat format = entry.getValue();

            VertexAttributeView src = source.attribute(key.semantic(), key.setIndex());
            VertexAttributeView dst = target.has(key.semantic(), key.setIndex())
                ? target.attribute(key.semantic(), key.setIndex())
                : target.addAttribute(key.semantic(), key.setIndex(), format);
            int components = format.components();

            for (int oldVertex = 0; oldVertex < oldToNew.length; oldVertex++) {
                int newVertex = oldToNew[oldVertex];
                if (newVertex < 0 || newVertex >= targetVertexCount) {
                    continue;
                }
                copyVertex(src, dst, oldVertex, newVertex, components, format);
            }
        }
    }

    private static void copyVertex(
        VertexAttributeView src,
        VertexAttributeView dst,
        int srcVertex,
        int dstVertex,
        int components,
        VertexFormat format
    ) {
        switch (format.kind()) {
            case FLOAT -> {
                for (int c = 0; c < components; c++) {
                    dst.setFloat(dstVertex, c, src.getFloat(srcVertex, c));
                }
            }
            case INT, SHORT, BYTE -> {
                for (int c = 0; c < components; c++) {
                    dst.setInt(dstVertex, c, src.getInt(srcVertex, c));
                }
            }
        }
    }
}
