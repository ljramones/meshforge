package org.dynamisengine.meshforge.ops.lod.gpu;

import org.dynamisengine.meshforge.ops.lod.MeshletLodLevelMetadata;
import org.dynamisengine.meshforge.ops.lod.MeshletLodMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MeshletLodUploadPrepTest {

    @Test
    void packsLevelsInExpectedOrder() {
        MeshletLodMetadata metadata = new MeshletLodMetadata(List.of(
            new MeshletLodLevelMetadata(0, 0, 64, 0.0f),
            new MeshletLodLevelMetadata(1, 64, 32, 0.75f)
        ));

        GpuMeshletLodPayload payload = MeshletLodUploadPrep.fromMetadata(metadata);

        assertEquals(2, payload.levelCount());
        assertEquals(0, payload.levelsOffsetInts());
        assertEquals(4, payload.levelsStrideInts());
        assertEquals(32, payload.levelsByteSize());
        assertArrayEquals(
            new int[] {
                0, 0, 64, Float.floatToRawIntBits(0.0f),
                1, 64, 32, Float.floatToRawIntBits(0.75f)
            },
            payload.levelsPayload()
        );
    }
}
