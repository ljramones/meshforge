package org.meshforge.loader;

import org.meshforge.core.mesh.MeshData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registry-based format dispatcher for mesh file loaders.
 */
public final class MeshLoaders {
    private final Map<String, MeshFileLoader> byExtension;

    private MeshLoaders(Map<String, MeshFileLoader> byExtension) {
        this.byExtension = byExtension;
    }

    public static MeshLoaders defaults() {
        Builder builder = builder();
        builder.register("obj", MeshLoaderFactory.obj());
        builder.register("stl", MeshLoaderFactory.stl());
        builder.register("ply", MeshLoaderFactory.ply());
        builder.register("off", MeshLoaderFactory.off());
        return builder.build();
    }

    /**
     * Registry with all planned format extensions wired.
     * Unimplemented formats throw with a clear message from the placeholder loader.
     */
    public static MeshLoaders planned() {
        return builder()
            .register("obj", MeshLoaderFactory.obj())
            .register("stl", MeshLoaderFactory.stl())
            .register("ply", MeshLoaderFactory.ply())
            .register("gltf", MeshLoaderFactory.gltf())
            .register("glb", MeshLoaderFactory.glb())
            .register("dae", MeshLoaderFactory.dae())
            .register("x3d", MeshLoaderFactory.x3d())
            .register("off", MeshLoaderFactory.off())
            .register("3mf", MeshLoaderFactory.threemf())
            .register("wrl", MeshLoaderFactory.wrl())
            .register("usd", MeshLoaderFactory.usd())
            .register("usda", MeshLoaderFactory.usd())
            .register("usdc", MeshLoaderFactory.usd())
            .register("usdz", MeshLoaderFactory.usd())
            .register("dxf", MeshLoaderFactory.dxf())
            .register("3ds", MeshLoaderFactory.tds())
            .register("ma", MeshLoaderFactory.mayaAscii())
            .register("step", MeshLoaderFactory.cadStep())
            .register("stp", MeshLoaderFactory.cadStep())
            .register("iges", MeshLoaderFactory.cadIges())
            .register("igs", MeshLoaderFactory.cadIges())
            .register("fxml", MeshLoaderFactory.fxml())
            .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public MeshData load(Path path) throws IOException {
        String ext = extension(path);
        MeshFileLoader loader = byExtension.get(ext);
        if (loader == null) {
            throw new IOException("Unsupported mesh format: " + ext + " for " + path);
        }
        return loader.load(path);
    }

    private static String extension(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static final class Builder {
        private final Map<String, MeshFileLoader> loaders = new HashMap<>();

        public Builder register(String extension, MeshFileLoader loader) {
            if (extension == null || extension.isBlank()) {
                throw new IllegalArgumentException("extension must not be blank");
            }
            if (loader == null) {
                throw new NullPointerException("loader");
            }
            String key = extension.toLowerCase(Locale.ROOT).replace(".", "");
            loaders.put(key, loader);
            return this;
        }

        public MeshLoaders build() {
            return new MeshLoaders(Map.copyOf(loaders));
        }
    }
}
