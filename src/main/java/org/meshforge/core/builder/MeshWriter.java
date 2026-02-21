package org.meshforge.core.builder;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;

import java.util.List;

public final class MeshWriter {
    private final MeshData mesh;

    public MeshWriter(VertexSchema schema, int vertexCount, int indexCount) {
        int[] indices = indexCount <= 0 ? null : new int[indexCount];
        List<Submesh> submeshes = indices == null
            ? List.of()
            : List.of(new Submesh(0, indices.length, "default"));
        this.mesh = new MeshData(Topology.TRIANGLES, schema, vertexCount, indices, submeshes);
    }

    public MeshWriter positions(float[] xyz) {
        setFloatAttribute(AttributeSemantic.POSITION, 0, xyz);
        return this;
    }

    public MeshWriter normals(float[] xyz) {
        setFloatAttribute(AttributeSemantic.NORMAL, 0, xyz);
        return this;
    }

    public MeshWriter tangents(float[] xyzw) {
        setFloatAttribute(AttributeSemantic.TANGENT, 0, xyzw);
        return this;
    }

    public MeshWriter uvs(float[] uv) {
        setFloatAttribute(AttributeSemantic.UV, 0, uv);
        return this;
    }

    public MeshWriter indices(int[] indices) {
        mesh.setIndices(indices);
        if (indices != null) {
            mesh.setSubmeshes(List.of(new Submesh(0, indices.length, "default")));
        }
        return this;
    }

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
