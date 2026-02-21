package org.meshforge.loader;

import org.meshforge.core.mesh.MeshData;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface MeshFileLoader {
    MeshData load(Path path) throws IOException;
}
