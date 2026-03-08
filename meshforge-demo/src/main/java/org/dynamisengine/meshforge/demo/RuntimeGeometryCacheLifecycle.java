package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.gpu.cache.RuntimeGeometryCacheIO;
import org.dynamisengine.meshforge.gpu.cache.RuntimeGeometryCachePolicy;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Simple cache lifecycle utility:
 * use cache when valid, otherwise rebuild from source and write cache.
 */
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
        return loadOrBuild(sourceMesh, RuntimeGeometryCachePolicy.sidecarPathFor(sourceMesh), loaders, packSpec, forceRebuild);
    }

    public static Result loadOrBuild(
        Path sourceMesh,
        Path cacheFile,
        MeshLoaders loaders,
        PackSpec packSpec,
        boolean forceRebuild
    ) throws IOException {
        if (!Files.isRegularFile(sourceMesh)) {
            throw new IOException("Missing source mesh: " + sourceMesh.toAbsolutePath());
        }

        if (!RuntimeGeometryCachePolicy.shouldRebuild(sourceMesh, cacheFile, forceRebuild)) {
            try {
                return new Result(RuntimeGeometryCacheIO.read(cacheFile), Source.CACHE);
            } catch (IOException ignored) {
                // Fall through to rebuild on incompatible/truncated/corrupt cache.
            }
        }

        MeshData loaded = loaders.load(sourceMesh);
        MeshData processed = Pipelines.realtimeFast(loaded);
        MeshPacker.RuntimePackPlan plan = MeshPacker.buildRuntimePlan(processed, packSpec);
        MeshPacker.RuntimePackWorkspace workspace = new MeshPacker.RuntimePackWorkspace();
        MeshPacker.packPlannedInto(plan, workspace);
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromRuntimeWorkspace(plan.layout(), workspace);
        RuntimeGeometryCacheIO.write(cacheFile, payload);
        return new Result(payload, Source.REBUILT);
    }
}
