package org.dynamisengine.meshforge.gpu.cache;

import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Canonical runtime geometry entry point for load-or-build cache flow.
 */
public final class RuntimeGeometryLoader {
    private final MeshLoaders loaders;
    private final PackSpec packSpec;

    public RuntimeGeometryLoader(MeshLoaders loaders, PackSpec packSpec) {
        this.loaders = Objects.requireNonNull(loaders, "loaders");
        this.packSpec = Objects.requireNonNull(packSpec, "packSpec");
    }

    public enum Source {
        CACHE,
        REBUILT
    }

    public record Result(RuntimeGeometryPayload payload, Source source) {
        public Result {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(source, "source");
        }
    }

    public Result load(Path sourceMesh) throws IOException {
        return load(sourceMesh, false);
    }

    public Result load(Path sourceMesh, boolean forceRebuild) throws IOException {
        return load(sourceMesh, RuntimeGeometryCachePolicy.sidecarPathFor(sourceMesh), forceRebuild);
    }

    public Result load(Path sourceMesh, Path cacheFile, boolean forceRebuild) throws IOException {
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
