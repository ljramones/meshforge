package org.dynamisengine.meshforge.mgi;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MgiMeshletLodModelTest {

    @Test
    void acceptsOrderedNonOverlappingLevels() {
        MgiMeshletLodData data = new MgiMeshletLodData(List.of(
            new MgiMeshletLodLevel(0, 0, 64, 0.0f),
            new MgiMeshletLodLevel(1, 64, 32, 0.75f)
        ));

        assertEquals(2, data.levels().size());
    }

    @Test
    void rejectsNonIncreasingLodLevels() {
        assertThrows(IllegalArgumentException.class, () -> new MgiMeshletLodData(List.of(
            new MgiMeshletLodLevel(0, 0, 32, 0.0f),
            new MgiMeshletLodLevel(0, 32, 16, 1.0f)
        )));
    }

    @Test
    void rejectsOverlappingRanges() {
        assertThrows(IllegalArgumentException.class, () -> new MgiMeshletLodData(List.of(
            new MgiMeshletLodLevel(0, 0, 32, 0.0f),
            new MgiMeshletLodLevel(1, 16, 16, 1.0f)
        )));
    }
}
