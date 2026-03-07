package org.dynamisengine.meshforge.ops.pipeline;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;

/**
 * Public class ValidateOp.
 */
public final class ValidateOp implements MeshOp {
    private static final float TANGENT_DOT_NORMAL_EPSILON = 1.0e-3f;
    private static final float HANDEDNESS_EPSILON = 1.0e-3f;

    @Override
    /**
     * Executes apply.
     * @param mesh parameter value
     * @param context parameter value
     * @return resulting value
     */
    public MeshData apply(MeshData mesh, MeshContext context) {
        if (!mesh.has(AttributeSemantic.POSITION, 0)) {
            throw new IllegalStateException("Mesh is missing required POSITION[0] attribute");
        }

        int[] indices = mesh.indicesOrNull();
        int vertexCount = mesh.vertexCount();

        if (mesh.topology() == Topology.TRIANGLES && indices != null && (indices.length % 3) != 0) {
            throw new IllegalStateException("Triangle index buffer length must be divisible by 3");
        }

        if (indices != null) {
            for (int i = 0; i < indices.length; i++) {
                int idx = indices[i];
                if (idx < 0 || idx >= vertexCount) {
                    throw new IllegalStateException("Index out of range at " + i + ": " + idx);
                }
            }
        }

        int indexCount = indices == null ? 0 : indices.length;
        for (Submesh submesh : mesh.submeshes()) {
            if (submesh.firstIndex() < 0) {
                throw new IllegalStateException("Submesh firstIndex must be >= 0");
            }
            if (submesh.indexCount() < 0) {
                throw new IllegalStateException("Submesh indexCount must be >= 0");
            }
            long end = (long) submesh.firstIndex() + submesh.indexCount();
            if (indices != null && end > indexCount) {
                throw new IllegalStateException("Submesh range exceeds index buffer");
            }
        }

        validateTangents(mesh);

        return mesh;
    }

    private static void validateTangents(MeshData mesh) {
        if (!mesh.has(AttributeSemantic.NORMAL, 0) || !mesh.has(AttributeSemantic.TANGENT, 0)) {
            return;
        }

        VertexAttributeView normal = mesh.attribute(AttributeSemantic.NORMAL, 0);
        VertexAttributeView tangent = mesh.attribute(AttributeSemantic.TANGENT, 0);

        if (normal.format().components() < 3) {
            throw new IllegalStateException("NORMAL[0] must have at least 3 components");
        }
        if (tangent.format().components() < 4) {
            throw new IllegalStateException("TANGENT[0] must have at least 4 components (xyz + handedness)");
        }

        for (int i = 0; i < mesh.vertexCount(); i++) {
            float nx = normal.getFloat(i, 0);
            float ny = normal.getFloat(i, 1);
            float nz = normal.getFloat(i, 2);

            float tx = tangent.getFloat(i, 0);
            float ty = tangent.getFloat(i, 1);
            float tz = tangent.getFloat(i, 2);
            float w = tangent.getFloat(i, 3);

            if (!Float.isFinite(tx) || !Float.isFinite(ty) || !Float.isFinite(tz) || !Float.isFinite(w)) {
                throw new IllegalStateException("TANGENT[0] contains non-finite values at vertex " + i);
            }

            float dot = nx * tx + ny * ty + nz * tz;
            if (Math.abs(dot) > TANGENT_DOT_NORMAL_EPSILON) {
                throw new IllegalStateException("TANGENT[0] is not orthogonal to NORMAL[0] at vertex " + i + " (dot=" + dot + ")");
            }

            if (Math.abs(Math.abs(w) - 1.0f) > HANDEDNESS_EPSILON) {
                throw new IllegalStateException("TANGENT[0] handedness must be +/-1 at vertex " + i + " (w=" + w + ")");
            }
        }
    }
}
