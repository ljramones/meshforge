package org.dynamisengine.meshforge.gpu.cache;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeGeometryCachePolicyTest {
    @Test
    void resolvesSidecarPath() {
        Path source = Path.of("models", "lucy.obj");
        Path cache = RuntimeGeometryCachePolicy.sidecarPathFor(source);
        assertEquals(Path.of("models", "lucy.mfgc"), cache);
    }

    @Test
    void rebuildsWhenCacheMissing() throws Exception {
        Path source = Files.createTempFile("meshforge-source-", ".obj");
        Path cache = source.resolveSibling("missing.mfgc");
        assertTrue(RuntimeGeometryCachePolicy.shouldRebuild(source, cache, false));
    }

    @Test
    void rebuildsWhenSourceIsNewer() throws Exception {
        Path source = Files.createTempFile("meshforge-source-", ".obj");
        Path cache = Files.createTempFile("meshforge-cache-", ".mfgc");
        Files.setLastModifiedTime(cache, FileTime.from(Instant.now().minusSeconds(60)));
        Files.setLastModifiedTime(source, FileTime.from(Instant.now()));
        assertTrue(RuntimeGeometryCachePolicy.shouldRebuild(source, cache, false));
    }

    @Test
    void doesNotRebuildWhenCacheIsNewer() throws Exception {
        Path source = Files.createTempFile("meshforge-source-", ".obj");
        Path cache = Files.createTempFile("meshforge-cache-", ".mfgc");
        Files.setLastModifiedTime(source, FileTime.from(Instant.now().minusSeconds(60)));
        Files.setLastModifiedTime(cache, FileTime.from(Instant.now()));
        assertFalse(RuntimeGeometryCachePolicy.shouldRebuild(source, cache, false));
    }
}
