package org.meshforge.loader;

import org.meshforge.core.mesh.MeshData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory methods for per-format mesh loaders.
 */
public final class MeshLoaderFactory {
    private MeshLoaderFactory() {
    }

    public static MeshFileLoader obj() {
        return ObjMeshLoader::load;
    }

    public static MeshFileLoader objFast() {
        return FastObjMeshLoader::load;
    }

    public static MeshFileLoader stl() {
        return StlMeshLoader::load;
    }

    public static MeshFileLoader ply() {
        return PlyMeshLoader::load;
    }

    public static MeshFileLoader gltf() {
        return unsupported("gltf");
    }

    public static MeshFileLoader glb() {
        return unsupported("glb");
    }

    public static MeshFileLoader dae() {
        return unsupported("dae");
    }

    public static MeshFileLoader x3d() {
        return unsupported("x3d");
    }

    public static MeshFileLoader off() {
        return OffMeshLoader::load;
    }

    public static MeshFileLoader threemf() {
        return unsupported("3mf");
    }

    public static MeshFileLoader wrl() {
        return unsupported("wrl");
    }

    public static MeshFileLoader usd() {
        return unsupported("usd/usda/usdc/usdz");
    }

    public static MeshFileLoader dxf() {
        return unsupported("dxf");
    }

    public static MeshFileLoader tds() {
        return unsupported("3ds");
    }

    public static MeshFileLoader mayaAscii() {
        return unsupported("ma");
    }

    public static MeshFileLoader cadStep() {
        return unsupported("step/stp");
    }

    public static MeshFileLoader cadIges() {
        return unsupported("iges/igs");
    }

    public static MeshFileLoader fxml() {
        return unsupported("fxml");
    }

    private static MeshFileLoader unsupported(String format) {
        return path -> {
            throw new IOException("Loader for format " + format + " is not implemented yet: " + path);
        };
    }
}
