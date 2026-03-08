package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.gpu.cache.RuntimeGeometryLoader;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Simple cache lifecycle utility:
 * use cache when valid, otherwise rebuild from source and write cache.
 */
@Deprecated(forRemoval = false)
public final class RuntimeGeometryCacheLifecycle {
    private RuntimeGeometryCacheLifecycle() {
    }

    public enum Source {
        CACHE,
        REBUILT
    }

    public record Result(RuntimeGeometryPayload payload, Source source) {
    }

    public static Result loadOrBuild(
        Path sourceMesh,
        MeshLoaders loaders,
        PackSpec packSpec,
        boolean forceRebuild
    ) throws IOException {
        RuntimeGeometryLoader.Result result = new RuntimeGeometryLoader(loaders, packSpec).load(sourceMesh, forceRebuild);
        return new Result(result.payload(), mapSource(result.source()));
    }

    public static Result loadOrBuild(
        Path sourceMesh,
        Path cacheFile,
        MeshLoaders loaders,
        PackSpec packSpec,
        boolean forceRebuild
    ) throws IOException {
        RuntimeGeometryLoader.Result result = new RuntimeGeometryLoader(loaders, packSpec)
            .load(sourceMesh, cacheFile, forceRebuild);
        return new Result(result.payload(), mapSource(result.source()));
    }

    private static Source mapSource(RuntimeGeometryLoader.Source source) {
        return source == RuntimeGeometryLoader.Source.CACHE ? Source.CACHE : Source.REBUILT;
    }
}
