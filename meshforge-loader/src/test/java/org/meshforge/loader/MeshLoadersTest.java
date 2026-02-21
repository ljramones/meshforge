package org.meshforge.loader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeshLoadersTest {
    @Test
    void rejectsUnsupportedExtension() {
        MeshLoaders loaders = MeshLoaders.defaults();
        assertThrows(IOException.class, () -> loaders.load(Path.of("model.unsupported")));
    }

    @Test
    void allowsCustomRegistration() throws Exception {
        MeshLoaders loaders = MeshLoaders.builder()
            .register("foo", path -> null)
            .build();

        assertEquals(null, loaders.load(Path.of("mesh.foo")));
    }

    @Test
    void plannedRegistryHasExplicitPlaceholderForKnownButUnimplementedFormat() {
        MeshLoaders loaders = MeshLoaders.planned();
        IOException ex = assertThrows(IOException.class, () -> loaders.load(Path.of("mesh.usd")));
        assertTrue(ex.getMessage().contains("not implemented yet"));
    }

    @Test
    void defaultsStillRejectUnknownFormats() {
        MeshLoaders loaders = MeshLoaders.defaults();
        IOException ex = assertThrows(IOException.class, () -> loaders.load(Path.of("mesh.xyz")));
        assertTrue(ex.getMessage().contains("Unsupported mesh format"));
    }
}
