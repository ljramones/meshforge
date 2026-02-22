package org.meshforge.demo;

import org.meshforge.pack.buffer.Meshlet;
import org.meshforge.pack.buffer.MeshletBufferView;
import org.meshforge.pack.buffer.PackedMesh;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

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
        long descriptorChecksum = descriptorChecksumCrc32(packed);
        return String.format(
            Locale.ROOT,
            "Meshlets: count=%d avgVerts=%.2f avgTris=%.2f minVerts=%d maxVerts=%d minTris=%d maxTris=%d avgCenterStep=%.4f descriptorStride=%dB descriptorBytes=%d descriptorCrc32=0x%08x",
            count,
            avgVerts,
            avgTris,
            minVerts,
            maxVerts,
            minTris,
            maxTris,
            avgCenterStep,
            packed.meshletDescriptorStrideBytes(),
            packed.meshletDescriptorBufferOrNull() == null ? 0 : packed.meshletDescriptorBufferOrNull().capacity(),
            descriptorChecksum
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

    public static String summarizeConeCulling(PackedMesh packed, String label, float vx, float vy, float vz) {
        if (packed == null || !packed.hasMeshlets()) {
            return String.format(Locale.ROOT, "%s view: 0 / 0 rejected (0.0%%)", label);
        }
        MeshletBufferView view = packed.meshletsOrNull();
        if (view == null || view.meshletCount() == 0) {
            return String.format(Locale.ROOT, "%s view: 0 / 0 rejected (0.0%%)", label);
        }
        float length = (float) Math.sqrt((vx * vx) + (vy * vy) + (vz * vz));
        if (length <= 0.0f) {
            return String.format(Locale.ROOT, "%s view: 0 / %d rejected (0.0%%)", label, view.meshletCount());
        }
        float dx = vx / length;
        float dy = vy / length;
        float dz = vz / length;
        int total = view.meshletCount();
        int rejected = 0;
        for (int i = 0; i < total; i++) {
            Meshlet meshlet = view.meshlet(i);
            float dot = (dx * meshlet.coneAxisX()) + (dy * meshlet.coneAxisY()) + (dz * meshlet.coneAxisZ());
            if (dot < meshlet.coneCutoffCos()) {
                rejected++;
            }
        }
        double pct = total == 0 ? 0.0 : ((double) rejected * 100.0) / total;
        return String.format(Locale.ROOT, "%s view: %d / %d rejected (%.1f%%)", label, rejected, total, pct);
    }

    private static long descriptorChecksumCrc32(PackedMesh packed) {
        ByteBuffer descriptor = packed == null ? null : packed.meshletDescriptorBufferOrNull();
        if (descriptor == null) {
            return 0L;
        }
        CRC32 crc32 = new CRC32();
        ByteBuffer copy = descriptor.asReadOnlyBuffer();
        copy.clear();
        crc32.update(copy);
        return crc32.getValue();
    }
}
