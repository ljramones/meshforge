package org.dynamisengine.meshforge.ops.tessellation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TessellationMetadataTest {

    @Test
    void acceptsOrderedRegions() {
        TessellationMetadata metadata = new TessellationMetadata(List.of(
            new TessellationRegionMetadata(0, 0, 12, 3, 1.0f, 0),
            new TessellationRegionMetadata(1, 12, 9, 4, 2.0f, 1)
        ));

        assertEquals(2, metadata.regionCount());
    }

    @Test
    void rejectsNonIncreasingSubmeshIndex() {
        assertThrows(IllegalArgumentException.class, () -> new TessellationMetadata(List.of(
            new TessellationRegionMetadata(1, 0, 12, 3, 1.0f, 0),
            new TessellationRegionMetadata(1, 12, 9, 4, 2.0f, 1)
        )));
    }
}
