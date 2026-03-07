package org.dynamisengine.meshforge.loader;

import org.dynamisengine.meshforge.core.mesh.MeshData;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Factory methods for per-format mesh loaders.
 */
public final class MeshLoaderFactory {
    private MeshLoaderFactory() {
    }

    /**
     * Executes obj.
     * @return resulting value
     */
    public static MeshFileLoader obj() {
        return ObjMeshLoader::load;
    }

    /**
     * Executes objFast.
     * @return resulting value
     */
    public static MeshFileLoader objFast() {
        return FastObjMeshLoader::load;
    }

    /**
     * Executes stl.
     * @return resulting value
     */
    public static MeshFileLoader stl() {
        return StlMeshLoader::load;
    }

    /**
     * Executes ply.
     * @return resulting value
     */
    public static MeshFileLoader ply() {
        return PlyMeshLoader::load;
    }

    /**
     * Executes gltf.
     * @return resulting value
     */
    public static MeshFileLoader gltf() {
        return new GltfMeshLoader();
    }

    /**
     * Executes glb.
     * @return resulting value
     */
    public static MeshFileLoader glb() {
        return new GltfMeshLoader();
    }

    /**
     * Executes dae.
     * @return resulting value
     */
    public static MeshFileLoader dae() {
        return unsupported("dae");
    }

    /**
     * Executes x3d.
     * @return resulting value
     */
    public static MeshFileLoader x3d() {
        return unsupported("x3d");
    }

    /**
     * Executes off.
     * @return resulting value
     */
    public static MeshFileLoader off() {
        return OffMeshLoader::load;
    }

    /**
     * Executes threemf.
     * @return resulting value
     */
    public static MeshFileLoader threemf() {
        return unsupported("3mf");
    }

    /**
     * Executes wrl.
     * @return resulting value
     */
    public static MeshFileLoader wrl() {
        return unsupported("wrl");
    }

    /**
     * Executes usd.
     * @return resulting value
     */
    public static MeshFileLoader usd() {
        return unsupported("usd/usda/usdc/usdz");
    }

    /**
     * Executes dxf.
     * @return resulting value
     */
    public static MeshFileLoader dxf() {
        return unsupported("dxf");
    }

    /**
     * Executes tds.
     * @return resulting value
     */
    public static MeshFileLoader tds() {
        return unsupported("3ds");
    }

    /**
     * Executes mayaAscii.
     * @return resulting value
     */
    public static MeshFileLoader mayaAscii() {
        return unsupported("ma");
    }

    /**
     * Executes cadStep.
     * @return resulting value
     */
    public static MeshFileLoader cadStep() {
        return unsupported("step/stp");
    }

    /**
     * Executes cadIges.
     * @return resulting value
     */
    public static MeshFileLoader cadIges() {
        return unsupported("iges/igs");
    }

    /**
     * Executes fxml.
     * @return resulting value
     */
    public static MeshFileLoader fxml() {
        return unsupported("fxml");
    }

    private static MeshFileLoader unsupported(String format) {
        return path -> {
            throw new IOException("Loader for format " + format + " is not implemented yet: " + path);
        };
    }
}
