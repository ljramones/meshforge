package org.dynamisengine.meshforge.pack.packer;

import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.util.List;

/**
 * Precomputed runtime packing plan bound to one mesh + spec shape.
 * This removes repeated per-call attribute/layout/index/submesh resolution.
 */
public final class RuntimePackPlan {
    final VertexLayout layout;
    final int vertexCount;
    final int stride;
    final float[] positionData;
    final int posOff;
    final float[] normalData;
    final boolean hasNormal;
    final int normalOff;
    final VertexFormat normalFormat;
    final float[] tangentData;
    final boolean hasTangent;
    final int tangentOff;
    final float[] uvData;
    final boolean hasUv;
    final int uvOff;
    final float[] colorData;
    final boolean hasColor;
    final int colorOff;
    final int[] jointsData;
    final VertexAttributeView jointsView;
    final boolean hasJoints;
    final int jointsOff;
    final float[] weightsData;
    final boolean hasWeights;
    final int weightsOff;
    final int[] indices;
    final PackSpec.IndexPolicy indexPolicy;
    final List<Submesh> submeshes;

    RuntimePackPlan(
        VertexLayout layout,
        int vertexCount,
        int stride,
        float[] positionData,
        int posOff,
        float[] normalData,
        boolean hasNormal,
        int normalOff,
        VertexFormat normalFormat,
        float[] tangentData,
        boolean hasTangent,
        int tangentOff,
        float[] uvData,
        boolean hasUv,
        int uvOff,
        float[] colorData,
        boolean hasColor,
        int colorOff,
        int[] jointsData,
        VertexAttributeView jointsView,
        boolean hasJoints,
        int jointsOff,
        float[] weightsData,
        boolean hasWeights,
        int weightsOff,
        int[] indices,
        PackSpec.IndexPolicy indexPolicy,
        List<Submesh> submeshes
    ) {
        this.layout = layout;
        this.vertexCount = vertexCount;
        this.stride = stride;
        this.positionData = positionData;
        this.posOff = posOff;
        this.normalData = normalData;
        this.hasNormal = hasNormal;
        this.normalOff = normalOff;
        this.normalFormat = normalFormat;
        this.tangentData = tangentData;
        this.hasTangent = hasTangent;
        this.tangentOff = tangentOff;
        this.uvData = uvData;
        this.hasUv = hasUv;
        this.uvOff = uvOff;
        this.colorData = colorData;
        this.hasColor = hasColor;
        this.colorOff = colorOff;
        this.jointsData = jointsData;
        this.jointsView = jointsView;
        this.hasJoints = hasJoints;
        this.jointsOff = jointsOff;
        this.weightsData = weightsData;
        this.hasWeights = hasWeights;
        this.weightsOff = weightsOff;
        this.indices = indices;
        this.indexPolicy = indexPolicy;
        this.submeshes = submeshes;
    }

    /**
     * Returns the packed vertex layout for this plan.
     *
     * @return vertex layout
     */
    public VertexLayout layout() {
        return layout;
    }

    /**
     * Returns vertex count for this plan.
     *
     * @return vertex count
     */
    public int vertexCount() {
        return vertexCount;
    }
}
