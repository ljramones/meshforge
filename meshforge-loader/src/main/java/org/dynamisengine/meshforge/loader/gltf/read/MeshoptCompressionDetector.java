package org.dynamisengine.meshforge.loader.gltf.read;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight detector for KHR_meshopt_compression in glTF JSON payloads.
 * <p>
 * This is a pre-parser hook used to gate decode policy until full glTF import is implemented.
 */
public final class MeshoptCompressionDetector {
    private static final byte[] TOKEN = "KHR_meshopt_compression".getBytes(StandardCharsets.UTF_8);

    private MeshoptCompressionDetector() {
    }

    /**
     * Executes containsMeshoptCompression.
     * @param path parameter value
     * @return resulting value
     */
    public static boolean containsMeshoptCompression(Path path) throws IOException {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        if (name.endsWith(".gltf")) {
            return containsToken(Files.readAllBytes(path), TOKEN);
        }
        if (name.endsWith(".glb")) {
            // For now, scan entire payload for the token to provide policy-gating behavior.
            return containsToken(Files.readAllBytes(path), TOKEN);
        }
        return false;
    }

    private static boolean containsToken(byte[] data, byte[] token) {
        if (data == null || token == null || data.length < token.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= data.length - token.length; i++) {
            for (int j = 0; j < token.length; j++) {
                if (data[i + j] != token[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}

