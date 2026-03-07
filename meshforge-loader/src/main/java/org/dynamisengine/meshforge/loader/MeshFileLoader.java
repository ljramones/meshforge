package org.dynamisengine.meshforge.loader;

import org.dynamisengine.meshforge.core.mesh.MeshData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Loader contract for converting an on-disk mesh asset into {@link MeshData}.
 */
@FunctionalInterface
public interface MeshFileLoader {
    /**
     * Loads mesh data with loader-default options.
     */
    MeshData load(Path path) throws IOException;

    /**
     * Loads mesh data with explicit options.
     * <p>
     * Default behavior delegates to {@link #load(Path)} for loaders that do not
     * currently consume option fields.
     */
    default MeshData load(Path path, MeshLoadOptions options) throws IOException {
        return load(path);
    }
}
