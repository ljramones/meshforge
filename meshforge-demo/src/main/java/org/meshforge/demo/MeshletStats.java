package org.meshforge.demo;

import org.meshforge.pack.buffer.Meshlet;
import org.meshforge.pack.buffer.MeshletBufferView;
import org.meshforge.pack.buffer.PackedMesh;

import java.util.List;
import java.util.Locale;

public final class MeshletStats {
    private MeshletStats() {
    }

    public static String summarize(PackedMesh packed) {
        if (packed == null || !packed.hasMeshlets()) {
            return "Meshlets: none";
        }
        MeshletBufferView view = packed.meshletsOrNull();
        if (view == null || view.meshletCount() == 0) {
            return "Meshlets: none";
        }

        int count = view.meshletCount();
        int totalVerts = 0;
        int totalTris = 0;
        int minVerts = Integer.MAX_VALUE;
        int maxVerts = Integer.MIN_VALUE;
        int minTris = Integer.MAX_VALUE;
        int maxTris = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            var m = view.meshlet(i);
            int v = m.uniqueVertexCount();
            int t = m.triangleCount();
            totalVerts += v;
            totalTris += t;
            if (v < minVerts) minVerts = v;
            if (v > maxVerts) maxVerts = v;
            if (t < minTris) minTris = t;
            if (t > maxTris) maxTris = t;
        }

        double avgVerts = (double) totalVerts / count;
        double avgTris = (double) totalTris / count;
        double avgCenterStep = averageCenterStep(view.asList());
        return String.format(
            Locale.ROOT,
            "Meshlets: count=%d avgVerts=%.2f avgTris=%.2f minVerts=%d maxVerts=%d minTris=%d maxTris=%d avgCenterStep=%.4f descriptorStride=%dB descriptorBytes=%d",
            count,
            avgVerts,
            avgTris,
            minVerts,
            maxVerts,
            minTris,
            maxTris,
            avgCenterStep,
            packed.meshletDescriptorStrideBytes(),
            packed.meshletDescriptorBufferOrNull() == null ? 0 : packed.meshletDescriptorBufferOrNull().capacity()
        );
    }

    public static double averageCenterStep(PackedMesh packed) {
        if (packed == null || !packed.hasMeshlets()) {
            return 0.0;
        }
        MeshletBufferView view = packed.meshletsOrNull();
        if (view == null) {
            return 0.0;
        }
        return averageCenterStep(view.asList());
    }

    private static double averageCenterStep(List<Meshlet> meshlets) {
        if (meshlets == null || meshlets.size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        int steps = 0;
        float px = 0.0f;
        float py = 0.0f;
        float pz = 0.0f;
        boolean hasPrev = false;
        for (Meshlet meshlet : meshlets) {
            var bounds = meshlet.bounds();
            float cx = (bounds.minX() + bounds.maxX()) * 0.5f;
            float cy = (bounds.minY() + bounds.maxY()) * 0.5f;
            float cz = (bounds.minZ() + bounds.maxZ()) * 0.5f;
            if (hasPrev) {
                double dx = cx - px;
                double dy = cy - py;
                double dz = cz - pz;
                sum += Math.sqrt(dx * dx + dy * dy + dz * dz);
                steps++;
            }
            px = cx;
            py = cy;
            pz = cz;
            hasPrev = true;
        }
        return steps == 0 ? 0.0 : (sum / steps);
    }

    public static void print(PackedMesh packed) {
        System.out.println(summarize(packed));
    }
}
