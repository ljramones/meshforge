package org.dynamisengine.meshforge.pack.buffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Utilities for packing meshlet descriptors into GPU-uploadable buffers.
 */
public final class MeshletBuffers {
    /**
     * 5 ints + 10 floats = 60 bytes before alignment.
     */
    public static final int RAW_DESCRIPTOR_BYTES = 60;

    private MeshletBuffers() {
    }

    /**
     * Executes descriptorStrideBytes.
     * @param alignmentBytes parameter value
     * @return resulting value
     */
    public static int descriptorStrideBytes(int alignmentBytes) {
        return align(RAW_DESCRIPTOR_BYTES, alignmentBytes <= 0 ? 1 : alignmentBytes);
    }

    /**
     * Executes packDescriptors.
     * @param meshlets parameter value
     * @param alignmentBytes parameter value
     * @return resulting value
     */
    public static ByteBuffer packDescriptors(MeshletBufferView meshlets, int alignmentBytes) {
        if (meshlets == null || meshlets.meshletCount() == 0) {
            return ByteBuffer.allocateDirect(0).order(ByteOrder.LITTLE_ENDIAN);
        }

        int stride = descriptorStrideBytes(alignmentBytes);
        int count = meshlets.meshletCount();
        ByteBuffer out = ByteBuffer.allocateDirect(count * stride).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < count; i++) {
            Meshlet m = meshlets.meshlet(i);
            int start = out.position();
            out.putInt(m.firstTriangle());
            out.putInt(m.triangleCount());
            out.putInt(m.firstIndex());
            out.putInt(m.indexCount());
            out.putInt(m.uniqueVertexCount());
            out.putFloat(m.bounds().minX());
            out.putFloat(m.bounds().minY());
            out.putFloat(m.bounds().minZ());
            out.putFloat(m.bounds().maxX());
            out.putFloat(m.bounds().maxY());
            out.putFloat(m.bounds().maxZ());
            out.putFloat(m.coneAxisX());
            out.putFloat(m.coneAxisY());
            out.putFloat(m.coneAxisZ());
            out.putFloat(m.coneCutoffCos());
            int wrote = out.position() - start;
            for (int pad = wrote; pad < stride; pad++) {
                out.put((byte) 0);
            }
        }

        out.flip();
        return out;
    }

    private static int align(int value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }
}
