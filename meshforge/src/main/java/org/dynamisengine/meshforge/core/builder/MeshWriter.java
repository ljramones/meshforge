package org.dynamisengine.meshforge.core.builder;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;

import java.util.List;

/**
 * Public class MeshWriter.
 */
public final class MeshWriter {
    private final MeshData mesh;

    /**
     * Creates a new {@code MeshWriter} instance.
     * @param schema parameter value
     * @param vertexCount parameter value
     * @param indexCount parameter value
     */
    public MeshWriter(VertexSchema schema, int vertexCount, int indexCount) {
        if (vertexCount == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("vertexCount exceeds supported limit: " + vertexCount);
        }
        if (indexCount == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("indexCount exceeds supported limit: " + indexCount);
        }
        if (indexCount < 0) {
            throw new IllegalArgumentException("indexCount must be >= 0");
        }
        int[] indices = indexCount <= 0 ? null : new int[indexCount];
        List<Submesh> submeshes = indices == null
            ? List.of()
            : List.of(new Submesh(0, indices.length, "default"));
        this.mesh = new MeshData(Topology.TRIANGLES, schema, vertexCount, indices, submeshes);
    }

    /**
     * Executes positions.
     * @param xyz parameter value
     * @return resulting value
     */
    public MeshWriter positions(float[] xyz) {
        setFloatAttribute(AttributeSemantic.POSITION, 0, xyz);
        return this;
    }

    /**
     * Executes normals.
     * @param xyz parameter value
     * @return resulting value
     */
    public MeshWriter normals(float[] xyz) {
        setFloatAttribute(AttributeSemantic.NORMAL, 0, xyz);
        return this;
    }

    /**
     * Executes tangents.
     * @param xyzw parameter value
     * @return resulting value
     */
    public MeshWriter tangents(float[] xyzw) {
        setFloatAttribute(AttributeSemantic.TANGENT, 0, xyzw);
        return this;
    }

    /**
     * Executes uvs.
     * @param uv parameter value
     * @return resulting value
     */
    public MeshWriter uvs(float[] uv) {
        setFloatAttribute(AttributeSemantic.UV, 0, uv);
        return this;
    }

    /**
     * Executes indices.
     * @param indices parameter value
     * @return resulting value
     */
    public MeshWriter indices(int[] indices) {
        mesh.setIndices(indices);
        if (indices != null) {
            mesh.setSubmeshes(List.of(new Submesh(0, indices.length, "default")));
        }
        return this;
    }

    /**
     * Creates build.
     * @return resulting value
     */
    public MeshData build() {
        return mesh;
    }

    private void setFloatAttribute(AttributeSemantic semantic, int setIndex, float[] src) {
        if (src == null) {
            throw new NullPointerException("src");
        }
        VertexAttributeView view = mesh.attribute(semantic, setIndex);
        int comps = view.format().components();
        if (src.length != mesh.vertexCount() * comps) {
            throw new IllegalArgumentException("Unexpected source length for " + semantic + "[" + setIndex + "]");
        }
        for (int v = 0; v < mesh.vertexCount(); v++) {
            int base = v * comps;
            for (int c = 0; c < comps; c++) {
                view.setFloat(v, c, src[base + c]);
            }
        }
    }
}
