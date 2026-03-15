package org.dynamisengine.meshforge.pack.packer;

import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Index buffer packing for both offline and runtime paths.
 */
final class IndexPacker {

    private IndexPacker() {
    }

    static PackedMesh.IndexBufferView packIndices(int[] indices, PackSpec.IndexPolicy policy) {
        if (indices.length == Integer.MAX_VALUE) {
            throw new IllegalStateException("indexCount exceeds supported limit: " + indices.length);
        }
        boolean canUse16 = canUse16Bit(indices);
        if (policy == PackSpec.IndexPolicy.FORCE_32) {
            canUse16 = false;
        }

        if (canUse16) {
            final ByteBuffer data;
            try {
                data = ByteBuffer.allocateDirect(Math.multiplyExact(indices.length, 2)).order(ByteOrder.LITTLE_ENDIAN);
            } catch (ArithmeticException ex) {
                throw new IllegalStateException("Index buffer size overflow for UINT16 with indexCount=" + indices.length, ex);
            }
            for (int value : indices) {
                data.putShort((short) value);
            }
            data.flip();
            return new PackedMesh.IndexBufferView(PackedMesh.IndexType.UINT16, data, indices.length);
        }

        final ByteBuffer data;
        try {
            data = ByteBuffer.allocateDirect(Math.multiplyExact(indices.length, 4)).order(ByteOrder.LITTLE_ENDIAN);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Index buffer size overflow for UINT32 with indexCount=" + indices.length, ex);
        }
        for (int value : indices) {
            data.putInt(value);
        }
        data.flip();
        return new PackedMesh.IndexBufferView(PackedMesh.IndexType.UINT32, data, indices.length);
    }

    static void packIndicesInto(int[] indices, PackSpec.IndexPolicy policy, RuntimePackWorkspace workspace) {
        if (indices.length == Integer.MAX_VALUE) {
            throw new IllegalStateException("indexCount exceeds supported limit: " + indices.length);
        }
        boolean canUse16 = canUse16Bit(indices);
        if (policy == PackSpec.IndexPolicy.FORCE_32) {
            canUse16 = false;
        }

        if (canUse16) {
            final ByteBuffer data;
            try {
                data = workspace.ensureIndexBufferCapacity(Math.multiplyExact(indices.length, 2));
            } catch (ArithmeticException ex) {
                throw new IllegalStateException("Index buffer size overflow for UINT16 with indexCount=" + indices.length, ex);
            }
            data.position(0);
            for (int value : indices) {
                data.putShort((short) value);
            }
            workspace.setIndexPayload(PackedMesh.IndexType.UINT16, indices.length, data.position());
            return;
        }

        final ByteBuffer data;
        try {
            data = workspace.ensureIndexBufferCapacity(Math.multiplyExact(indices.length, 4));
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Index buffer size overflow for UINT32 with indexCount=" + indices.length, ex);
        }
        data.position(0);
        for (int value : indices) {
            data.putInt(value);
        }
        workspace.setIndexPayload(PackedMesh.IndexType.UINT32, indices.length, data.position());
    }

    private static boolean canUse16Bit(int[] indices) {
        for (int value : indices) {
            if (value < 0) {
                throw new IllegalStateException("Index buffer contains negative index: " + value);
            }
            if ((value & 0xFFFF0000) != 0) {
                return false;
            }
        }
        return true;
    }
}
