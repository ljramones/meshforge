package org.meshforge.ops.optimize;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.MorphTarget;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;

import java.util.Arrays;
import java.util.List;
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

        return reorderAndCompact(mesh, remappedIndices, oldToNew, newVertexCount);
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
        remapped.setMorphTargets(copyMorphTargets(mesh.morphTargets()));
        remapped.setBounds(mesh.boundsOrNull());
        return remapped;
    }

    public static MeshData reorderAndCompact(
        MeshData mesh,
        int[] remappedIndices,
        int[] oldToNew,
        int newVertexCount
    ) {
        return reorderAndCompact(mesh, remappedIndices, oldToNew, newVertexCount, mesh.submeshes());
    }

    public static MeshData reorderAndCompact(
        MeshData mesh,
        int[] remappedIndices,
        int[] oldToNew,
        int newVertexCount,
        List<Submesh> submeshes
    ) {
        MeshData compacted = new MeshData(
            mesh.topology(),
            mesh.schema(),
            newVertexCount,
            remappedIndices,
            submeshes
        );

        copyAttributes(mesh, compacted, oldToNew, newVertexCount);
        compacted.setMorphTargets(remapMorphTargets(mesh.morphTargets(), oldToNew, newVertexCount));
        compacted.setBounds(mesh.boundsOrNull());
        return compacted;
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

    private static List<MorphTarget> copyMorphTargets(List<MorphTarget> source) {
        List<MorphTarget> out = new java.util.ArrayList<>(source.size());
        for (MorphTarget morph : source) {
            out.add(new MorphTarget(
                morph.name(),
                morph.positionDeltas(),
                morph.normalDeltas(),
                morph.tangentDeltas()
            ));
        }
        return out;
    }

    private static List<MorphTarget> remapMorphTargets(List<MorphTarget> source, int[] oldToNew, int newVertexCount) {
        if (source.isEmpty()) {
            return List.of();
        }
        int required = Math.multiplyExact(newVertexCount, 3);
        List<MorphTarget> out = new java.util.ArrayList<>(source.size());
        for (MorphTarget morph : source) {
            float[] pos = remapMorphArray(oldToNew, morph.positionDeltas(), required, newVertexCount);
            float[] nrm = morph.normalDeltas() == null ? null : remapMorphArray(oldToNew, morph.normalDeltas(), required, newVertexCount);
            float[] tan = morph.tangentDeltas() == null ? null : remapMorphArray(oldToNew, morph.tangentDeltas(), required, newVertexCount);
            out.add(new MorphTarget(morph.name(), pos, nrm, tan));
        }
        return out;
    }

    private static float[] remapMorphArray(int[] oldToNew, float[] src, int required, int newVertexCount) {
        float[] dst = new float[required];
        for (int oldVertex = 0; oldVertex < oldToNew.length; oldVertex++) {
            int newVertex = oldToNew[oldVertex];
            if (newVertex < 0 || newVertex >= newVertexCount) {
                continue;
            }
            int so = oldVertex * 3;
            int doff = newVertex * 3;
            dst[doff] = src[so];
            dst[doff + 1] = src[so + 1];
            dst[doff + 2] = src[so + 2];
        }
        return dst;
    }
}
