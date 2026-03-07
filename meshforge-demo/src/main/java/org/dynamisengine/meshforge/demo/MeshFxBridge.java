package org.dynamisengine.meshforge.demo;

import javafx.collections.ObservableFloatArray;
import javafx.collections.ObservableIntegerArray;
import javafx.scene.shape.TriangleMesh;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.topology.Topology;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

final class MeshFxBridge {
    private static final int MAX_POINTS_PER_MESH = 30_000;
    private static final int MAX_TRIANGLES_PER_MESH = 20_000;

    private MeshFxBridge() {
    }

    static List<TriangleMesh> toTriangleMeshes(MeshData mesh) {
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

        List<TriangleMesh> parts = new ArrayList<>();
        ChunkBuilder chunk = new ChunkBuilder(positions);
        for (int i = 0; i < indices.length; i += 3) {
            int a = indices[i];
            int b = indices[i + 1];
            int c = indices[i + 2];
            if (!chunk.canAccept(a, b, c)) {
                parts.add(chunk.build());
                chunk = new ChunkBuilder(positions);
            }
            chunk.addTriangle(a, b, c);
        }
        if (chunk.hasFaces()) {
            parts.add(chunk.build());
        }
        return parts;
    }

    private static final class ChunkBuilder {
        private final float[] sourcePositions;
        private final HashMap<Integer, Integer> remap = new HashMap<>();
        private float[] points = new float[3 * 1024];
        private int[] faces = new int[6 * 1024];
        private int pointFloats;
        private int faceInts;
        private int triangleCount;

        ChunkBuilder(float[] sourcePositions) {
            this.sourcePositions = sourcePositions;
        }

        boolean canAccept(int a, int b, int c) {
            if (triangleCount >= MAX_TRIANGLES_PER_MESH) {
                return false;
            }
            int needed = 0;
            if (!remap.containsKey(a)) {
                needed++;
            }
            if (!remap.containsKey(b)) {
                needed++;
            }
            if (!remap.containsKey(c)) {
                needed++;
            }
            return (remap.size() + needed) <= MAX_POINTS_PER_MESH;
        }

        void addTriangle(int a, int b, int c) {
            int ra = remapIndex(a);
            int rb = remapIndex(b);
            int rc = remapIndex(c);

            ensureFaceCapacity(faceInts + 6);
            faces[faceInts++] = ra;
            faces[faceInts++] = 0;
            faces[faceInts++] = rb;
            faces[faceInts++] = 0;
            faces[faceInts++] = rc;
            faces[faceInts++] = 0;
            triangleCount++;
        }

        boolean hasFaces() {
            return faceInts > 0;
        }

        TriangleMesh build() {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getTexCoords().setAll(0.0f, 0.0f);
            ObservableFloatArray meshPoints = mesh.getPoints();
            meshPoints.setAll(points, 0, pointFloats);
            ObservableIntegerArray meshFaces = mesh.getFaces();
            meshFaces.setAll(faces, 0, faceInts);
            return mesh;
        }

        private int remapIndex(int sourceIndex) {
            Integer existing = remap.get(sourceIndex);
            if (existing != null) {
                return existing;
            }
            int mapped = remap.size();
            remap.put(sourceIndex, mapped);

            ensurePointCapacity(pointFloats + 3);
            int p = sourceIndex * 3;
            points[pointFloats++] = sourcePositions[p];
            points[pointFloats++] = sourcePositions[p + 1];
            points[pointFloats++] = sourcePositions[p + 2];
            return mapped;
        }

        private void ensurePointCapacity(int needed) {
            if (needed <= points.length) {
                return;
            }
            int next = Math.max(needed, points.length * 2);
            float[] resized = new float[next];
            System.arraycopy(points, 0, resized, 0, pointFloats);
            points = resized;
        }

        private void ensureFaceCapacity(int needed) {
            if (needed <= faces.length) {
                return;
            }
            int next = Math.max(needed, faces.length * 2);
            int[] resized = new int[next];
            System.arraycopy(faces, 0, resized, 0, faceInts);
            faces = resized;
        }
    }
}
