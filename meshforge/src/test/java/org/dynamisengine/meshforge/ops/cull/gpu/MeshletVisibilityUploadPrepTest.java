package org.dynamisengine.meshforge.ops.cull.gpu;

import org.dynamisengine.meshforge.core.bounds.Aabbf;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeshletVisibilityUploadPrepTest {

    @Test
    void handlesZeroMeshlets() {
        GpuMeshletVisibilityPayload payload = MeshletVisibilityUploadPrep.fromMeshletBounds(List.of());

        assertEquals(0, payload.meshletCount());
        assertEquals(0, payload.boundsOffsetFloats());
        assertEquals(6, payload.boundsStrideFloats());
        assertEquals(0, payload.boundsPayload().length);
        assertEquals(0, payload.boundsByteSize());
        assertEquals(24, payload.boundsStrideBytes());
        assertEquals(0, payload.toBoundsByteBuffer().remaining());
    }

    @Test
    void packsSingleMeshletInExpectedOrder() {
        GpuMeshletVisibilityPayload payload = MeshletVisibilityUploadPrep.fromMeshletBounds(
            List.of(new Aabbf(1f, 2f, 3f, 4f, 5f, 6f))
        );

        assertEquals(1, payload.meshletCount());
        assertEquals(24, payload.boundsByteSize());
        assertArrayEquals(new float[] {1f, 2f, 3f, 4f, 5f, 6f}, payload.boundsPayload());

        ByteBuffer bytes = payload.toBoundsByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1f, bytes.getFloat());
        assertEquals(2f, bytes.getFloat());
        assertEquals(3f, bytes.getFloat());
        assertEquals(4f, bytes.getFloat());
        assertEquals(5f, bytes.getFloat());
        assertEquals(6f, bytes.getFloat());
    }

    @Test
    void packsMultipleMeshletsWithConsistentCountsAndStride() {
        GpuMeshletVisibilityPayload payload = MeshletVisibilityUploadPrep.fromMeshletBounds(
            List.of(
                new Aabbf(0f, 0f, 0f, 1f, 1f, 1f),
                new Aabbf(-1f, -2f, -3f, 2f, 3f, 4f)
            )
        );

        assertEquals(2, payload.meshletCount());
        assertEquals(0, payload.boundsOffsetFloats());
        assertEquals(6, payload.boundsStrideFloats());
        assertEquals(12, payload.boundsPayload().length);
        assertEquals(48, payload.boundsByteSize());
        assertEquals(24, payload.boundsStrideBytes());
        assertArrayEquals(
            new float[] {0f, 0f, 0f, 1f, 1f, 1f, -1f, -2f, -3f, 2f, 3f, 4f},
            payload.boundsPayload()
        );
    }

    @Test
    void rejectsNullBoundsEntry() {
        assertThrows(NullPointerException.class,
            () -> MeshletVisibilityUploadPrep.fromMeshletBounds(List.of(new Aabbf(0f, 0f, 0f, 1f, 1f, 1f), null)));
    }
}
