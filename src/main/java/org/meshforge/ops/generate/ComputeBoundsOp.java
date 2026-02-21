package org.meshforge.ops.generate;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.bounds.Aabbf;
import org.meshforge.core.bounds.Boundsf;
import org.meshforge.core.bounds.Spheref;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.ops.pipeline.MeshContext;
import org.meshforge.ops.pipeline.MeshOp;

public final class ComputeBoundsOp implements MeshOp {
    @Override
    public MeshData apply(MeshData mesh, MeshContext context) {
        VertexAttributeView position = require(mesh, AttributeSemantic.POSITION, 0);
        float[] positions = requireFloat(position, "POSITION");
        int components = position.format().components();
        if (components < 3) {
            throw new IllegalStateException("POSITION[0] must have at least 3 components");
        }

        int vertexCount = mesh.vertexCount();
        if (vertexCount == 0) {
            mesh.setBounds(null);
            return mesh;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < vertexCount; i++) {
            int p = i * components;
            float x = positions[p];
            float y = positions[p + 1];
            float z = positions[p + 2];
            if (x < minX) {
                minX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (z < minZ) {
                minZ = z;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y > maxY) {
                maxY = y;
            }
            if (z > maxZ) {
                maxZ = z;
            }
        }

        float centerX = (minX + maxX) * 0.5f;
        float centerY = (minY + maxY) * 0.5f;
        float centerZ = (minZ + maxZ) * 0.5f;

        float radiusSq = 0.0f;
        for (int i = 0; i < vertexCount; i++) {
            int p = i * components;
            float dx = positions[p] - centerX;
            float dy = positions[p + 1] - centerY;
            float dz = positions[p + 2] - centerZ;
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > radiusSq) {
                radiusSq = distSq;
            }
        }

        Boundsf bounds = new Boundsf(
            new Aabbf(minX, minY, minZ, maxX, maxY, maxZ),
            new Spheref(centerX, centerY, centerZ, (float) Math.sqrt(radiusSq))
        );
        mesh.setBounds(bounds);
        return mesh;
    }

    private static VertexAttributeView require(MeshData mesh, AttributeSemantic semantic, int setIndex) {
        if (!mesh.has(semantic, setIndex)) {
            throw new IllegalStateException("Missing required attribute: " + semantic + "[" + setIndex + "]");
        }
        return mesh.attribute(semantic, setIndex);
    }

    private static float[] requireFloat(VertexAttributeView view, String label) {
        float[] values = view.rawFloatArrayOrNull();
        if (values == null) {
            throw new IllegalStateException(label + " must be float-backed");
        }
        return values;
    }
}
