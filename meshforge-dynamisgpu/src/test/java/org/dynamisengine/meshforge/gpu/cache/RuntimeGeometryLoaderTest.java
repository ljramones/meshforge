package org.dynamisengine.meshforge.gpu.cache;

import org.dynamisengine.meshforge.api.Packers;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeGeometryLoaderTest {

    @Test
    void loadOrBuildUsesCacheOnSecondCall(@TempDir Path dir) throws Exception {
        Path source = writeMinimalObj(dir.resolve("triangle.obj"));
        RuntimeGeometryLoader loader = new RuntimeGeometryLoader(MeshLoaders.defaultsFast(), Packers.realtimeFast());

        RuntimeGeometryLoader.Result first = loader.load(source);
        RuntimeGeometryLoader.Result second = loader.load(source);

        assertEquals(RuntimeGeometryLoader.Source.REBUILT, first.source());
        assertEquals(RuntimeGeometryLoader.Source.CACHE, second.source());
        assertTrue(Files.isRegularFile(RuntimeGeometryCachePolicy.sidecarPathFor(source)));
    }

    @Test
    void loadOrBuildRebuildsWhenSourceIsNewer(@TempDir Path dir) throws Exception {
        Path source = writeMinimalObj(dir.resolve("triangle.obj"));
        RuntimeGeometryLoader loader = new RuntimeGeometryLoader(MeshLoaders.defaultsFast(), Packers.realtimeFast());

        loader.load(source);
        Files.setLastModifiedTime(source, FileTime.from(Instant.now().plusSeconds(2)));

        RuntimeGeometryLoader.Result refreshed = loader.load(source);
        assertEquals(RuntimeGeometryLoader.Source.REBUILT, refreshed.source());
    }

    @Test
    void loadOrBuildHonorsForceRebuild(@TempDir Path dir) throws Exception {
        Path source = writeMinimalObj(dir.resolve("triangle.obj"));
        RuntimeGeometryLoader loader = new RuntimeGeometryLoader(MeshLoaders.defaultsFast(), Packers.realtimeFast());

        loader.load(source);
        RuntimeGeometryLoader.Result forced = loader.load(source, true);

        assertEquals(RuntimeGeometryLoader.Source.REBUILT, forced.source());
    }

    private static Path writeMinimalObj(Path path) throws IOException {
        String data = """
            v 0 0 0
            v 1 0 0
            v 0 1 0
            f 1 2 3
            """;
        Files.writeString(path, data);
        return path;
    }
}
