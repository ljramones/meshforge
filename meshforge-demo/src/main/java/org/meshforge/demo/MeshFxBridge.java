package org.meshforge.demo;

import javafx.collections.ObservableFloatArray;
import javafx.collections.ObservableIntegerArray;
import javafx.scene.shape.TriangleMesh;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.topology.Topology;

final class MeshFxBridge {
    private MeshFxBridge() {
    }

    static TriangleMesh toTriangleMesh(MeshData mesh) {
        if (mesh.topology() != Topology.TRIANGLES) {
            throw new IllegalStateException("JavaFX viewer supports TRIANGLES only. Mesh topology is " + mesh.topology());
        }
        if (!mesh.has(AttributeSemantic.POSITION, 0)) {
            throw new IllegalStateException("MeshData is missing POSITION[0]");
        }

        float[] positions = mesh.attribute(AttributeSemantic.POSITION, 0).rawFloatArrayOrNull();
        if (positions == null) {
            throw new IllegalStateException("POSITION[0] must be float-backed");
        }
        if (positions.length == 0 || (positions.length % 3) != 0) {
            throw new IllegalStateException("POSITION[0] must contain xyz triplets, found length=" + positions.length);
        }
        for (int i = 0; i < positions.length; i++) {
            if (!Float.isFinite(positions[i])) {
                throw new IllegalStateException("POSITION[0] contains non-finite value at element " + i);
            }
        }

        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length == 0) {
            throw new IllegalStateException("MeshData has no index buffer for JavaFX rendering");
        }
        if ((indices.length % 3) != 0) {
            throw new IllegalStateException("Triangle index buffer length must be divisible by 3, found " + indices.length);
        }
        int vertexCount = positions.length / 3;
        for (int i = 0; i < indices.length; i++) {
            int idx = indices[i];
            if (idx < 0 || idx >= vertexCount) {
                throw new IllegalStateException("Index out of range at " + i + ": " + idx + " (vertexCount=" + vertexCount + ")");
            }
        }

        TriangleMesh fx = new TriangleMesh();
        ObservableFloatArray points = fx.getPoints();
        points.setAll(positions);

        // JavaFX requires at least one texCoord entry.
        fx.getTexCoords().setAll(0.0f, 0.0f);

        ObservableIntegerArray faces = fx.getFaces();
        int[] fxFaces = new int[indices.length * 2];
        for (int i = 0; i < indices.length; i++) {
            fxFaces[i * 2] = indices[i];
            fxFaces[i * 2 + 1] = 0;
        }
        faces.setAll(fxFaces);

        return fx;
    }
}
