package org.dynamisengine.meshforge.ops.tessellation.gpu;

import org.dynamisengine.meshforge.ops.tessellation.TessellationMetadata;
import org.dynamisengine.meshforge.ops.tessellation.TessellationRegionMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TessellationUploadPrepTest {

    @Test
    void packsRegionsInExpectedOrder() {
        TessellationMetadata metadata = new TessellationMetadata(List.of(
            new TessellationRegionMetadata(0, 0, 12, 3, 1.0f, 0),
            new TessellationRegionMetadata(1, 12, 9, 4, 2.0f, 1)
        ));

        GpuTessellationPayload payload = TessellationUploadPrep.fromMetadata(metadata);

        assertEquals(2, payload.regionCount());
        assertEquals(0, payload.regionsOffsetInts());
        assertEquals(6, payload.regionsStrideInts());
        assertEquals(48, payload.regionsByteSize());
        assertArrayEquals(
            new int[] {
                0, 0, 12, 3, Float.floatToRawIntBits(1.0f), 0,
                1, 12, 9, 4, Float.floatToRawIntBits(2.0f), 1
            },
            payload.regionsPayload()
        );
    }
}
