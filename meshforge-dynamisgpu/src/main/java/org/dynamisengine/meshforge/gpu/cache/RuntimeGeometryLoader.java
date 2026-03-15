package org.dynamisengine.meshforge.gpu.cache;

import org.dynamisengine.meshforge.api.Pipelines;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.gpu.MeshForgeGpuBridge;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.pack.packer.MeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimeMeshPacker;
import org.dynamisengine.meshforge.pack.packer.RuntimePackPlan;
import org.dynamisengine.meshforge.pack.packer.RuntimePackWorkspace;
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

    public enum PrebuildStatus {
        REUSED,
        BUILT
    }

    public record Result(RuntimeGeometryPayload payload, Source source) {
        public Result {
            Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(source, "source");
        }
    }

    public record PrebuildResult(Path sourceMesh, Path cacheFile, PrebuildStatus status) {
        public PrebuildResult {
            Objects.requireNonNull(sourceMesh, "sourceMesh");
            Objects.requireNonNull(cacheFile, "cacheFile");
            Objects.requireNonNull(status, "status");
        }
    }

    public Result load(Path sourceMesh) throws IOException {
        return load(sourceMesh, false);
    }

    public Result load(Path sourceMesh, boolean forceRebuild) throws IOException {
        return load(sourceMesh, RuntimeGeometryCachePolicy.sidecarPathFor(sourceMesh), forceRebuild);
    }

    public Result load(Path sourceMesh, Path cacheFile, boolean forceRebuild) throws IOException {
        Prepared prepared = preparePayload(sourceMesh, cacheFile, forceRebuild);
        return new Result(prepared.payload(), prepared.source());
    }

    public PrebuildResult prebuild(Path sourceMesh) throws IOException {
        return prebuild(sourceMesh, false);
    }

    public PrebuildResult prebuild(Path sourceMesh, boolean forceRebuild) throws IOException {
        return prebuild(sourceMesh, RuntimeGeometryCachePolicy.sidecarPathFor(sourceMesh), forceRebuild);
    }

    public PrebuildResult prebuild(Path sourceMesh, Path cacheFile, boolean forceRebuild) throws IOException {
        Prepared prepared = preparePayload(sourceMesh, cacheFile, forceRebuild);
        PrebuildStatus status = prepared.source() == Source.CACHE ? PrebuildStatus.REUSED : PrebuildStatus.BUILT;
        return new PrebuildResult(sourceMesh, cacheFile, status);
    }

    private Prepared preparePayload(Path sourceMesh, Path cacheFile, boolean forceRebuild) throws IOException {
        if (!Files.isRegularFile(sourceMesh)) {
            throw new IOException("Missing source mesh: " + sourceMesh.toAbsolutePath());
        }

        if (!RuntimeGeometryCachePolicy.shouldRebuild(sourceMesh, cacheFile, forceRebuild)) {
            try {
                return new Prepared(RuntimeGeometryCacheIO.read(cacheFile), Source.CACHE);
            } catch (IOException ignored) {
                // Fall through to rebuild on incompatible/truncated/corrupt cache.
            }
        }

        MeshData loaded = loaders.load(sourceMesh);
        MeshData processed = Pipelines.realtimeFast(loaded);
        RuntimePackPlan plan = RuntimeMeshPacker.buildRuntimePlan(processed, packSpec);
        RuntimePackWorkspace workspace = new RuntimePackWorkspace();
        RuntimeMeshPacker.packPlannedInto(plan, workspace);
        RuntimeGeometryPayload payload = MeshForgeGpuBridge.payloadFromRuntimeWorkspace(plan.layout(), workspace);
        RuntimeGeometryCacheIO.write(cacheFile, payload);
        return new Prepared(payload, Source.REBUILT);
    }

    private record Prepared(RuntimeGeometryPayload payload, Source source) {
    }
}
