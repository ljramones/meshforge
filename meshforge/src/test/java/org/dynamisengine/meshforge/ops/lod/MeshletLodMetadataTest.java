package org.dynamisengine.meshforge.ops.lod;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MeshletLodMetadataTest {

    @Test
    void acceptsOrderedNonOverlappingLevels() {
        MeshletLodMetadata metadata = new MeshletLodMetadata(List.of(
            new MeshletLodLevelMetadata(0, 0, 64, 0.0f),
            new MeshletLodLevelMetadata(1, 64, 32, 0.75f),
            new MeshletLodLevelMetadata(2, 96, 16, 1.25f)
        ));

        assertEquals(3, metadata.levelCount());
        assertEquals(16, metadata.levels().get(2).meshletCount());
    }

    @Test
    void rejectsNonIncreasingLevels() {
        assertThrows(IllegalArgumentException.class, () -> new MeshletLodMetadata(List.of(
            new MeshletLodLevelMetadata(0, 0, 32, 0.0f),
            new MeshletLodLevelMetadata(0, 32, 16, 0.8f)
        )));
    }

    @Test
    void rejectsOverlappingRanges() {
        assertThrows(IllegalArgumentException.class, () -> new MeshletLodMetadata(List.of(
            new MeshletLodLevelMetadata(0, 0, 32, 0.0f),
            new MeshletLodLevelMetadata(1, 16, 16, 1.0f)
        )));
    }
}
