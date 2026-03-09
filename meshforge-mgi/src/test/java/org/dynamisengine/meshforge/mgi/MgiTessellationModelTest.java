package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MgiTessellationModelTest {

    @Test
    void acceptsOrderedRegions() {
        MgiTessellationData data = new MgiTessellationData(List.of(
            new MgiTessellationRegion(0, 0, 12, 3, 1.0f, 0),
            new MgiTessellationRegion(1, 12, 9, 4, 2.0f, 1)
        ));

        assertEquals(2, data.regions().size());
    }

    @Test
    void rejectsNonIncreasingSubmeshIndex() {
        assertThrows(IllegalArgumentException.class, () -> new MgiTessellationData(List.of(
            new MgiTessellationRegion(1, 0, 12, 3, 1.0f, 0),
            new MgiTessellationRegion(1, 12, 9, 4, 2.0f, 1)
        )));
    }

    @Test
    void rejectsInvalidPatchControlPointCount() {
        assertThrows(IllegalArgumentException.class,
            () -> new MgiTessellationRegion(0, 0, 12, 2, 1.0f, 0));
    }

    @Test
    void rejectsNonPositiveTessellationLevel() {
        assertThrows(IllegalArgumentException.class,
            () -> new MgiTessellationRegion(0, 0, 12, 3, 0.0f, 0));
    }
}
