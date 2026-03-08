package org.dynamisengine.meshforge.gpu.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Runtime geometry cache placement and rebuild policy.
 */
public final class RuntimeGeometryCachePolicy {
    private RuntimeGeometryCachePolicy() {
    }

    /**
     * Resolves sidecar cache path next to the source asset.
     * Example: {@code models/lucy.obj -> models/lucy.mfgc}.
     *
     * @param sourceAsset source mesh path
     * @return sidecar cache path
     */
    public static Path sidecarPathFor(Path sourceAsset) {
        if (sourceAsset == null) {
            throw new NullPointerException("sourceAsset");
        }
        String name = sourceAsset.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot <= 0 ? name : name.substring(0, dot);
        String cacheName = base + ".mfgc";
        Path parent = sourceAsset.getParent();
        return parent == null ? Path.of(cacheName) : parent.resolve(cacheName);
    }

    /**
     * Returns whether the cache should be rebuilt.
     * Rebuild triggers:
     * - forced rebuild
     * - missing cache
     * - source newer than cache
     *
     * @param sourceAsset source asset path
     * @param cacheFile cache file path
     * @param forceRebuild force rebuild flag
     * @return true when cache must be rebuilt
     * @throws IOException when source timestamp cannot be read
     */
    public static boolean shouldRebuild(Path sourceAsset, Path cacheFile, boolean forceRebuild) throws IOException {
        if (sourceAsset == null) {
            throw new NullPointerException("sourceAsset");
        }
        if (cacheFile == null) {
            throw new NullPointerException("cacheFile");
        }
        if (forceRebuild) {
            return true;
        }
        if (!Files.isRegularFile(cacheFile)) {
            return true;
        }
        FileTime sourceTime = Files.getLastModifiedTime(sourceAsset);
        FileTime cacheTime = Files.getLastModifiedTime(cacheFile);
        return sourceTime.compareTo(cacheTime) > 0;
    }
}
