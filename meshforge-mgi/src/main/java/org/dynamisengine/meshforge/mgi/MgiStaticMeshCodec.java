package org.dynamisengine.meshforge.mgi;

import java.io.IOException;

/**
 * Minimal static mesh geometry payload codec for MGI v1.
 *
 * <p>Delegates to {@link MgiStaticMeshWriter} and {@link MgiStaticMeshReader}.
 */
public final class MgiStaticMeshCodec {
    private final MgiStaticMeshWriter writer = new MgiStaticMeshWriter();
    private final MgiStaticMeshReader reader = new MgiStaticMeshReader();

    public byte[] write(MgiStaticMesh mesh) throws IOException {
        return writer.write(mesh);
    }

    public MgiStaticMesh read(byte[] bytes) throws IOException {
        return reader.read(bytes);
    }
}
