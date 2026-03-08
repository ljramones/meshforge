package org.dynamisengine.meshforge.gpu.cache;

import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.gpu.RuntimeGeometryPayload;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Binary cache reader/writer for runtime geometry payloads.
 */
public final class RuntimeGeometryCacheIO {
    private static final int MAGIC = 0x4D464743; // MFGC
    private static final int VERSION = 1;

    private RuntimeGeometryCacheIO() {
    }

    public static void write(Path path, RuntimeGeometryPayload payload) throws IOException {
        if (path == null) {
            throw new NullPointerException("path");
        }
        if (payload == null) {
            throw new NullPointerException("payload");
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(payload.vertexCount());
            out.writeInt(payload.indexCount());
            out.writeInt(payload.indexType() == null ? -1 : payload.indexType().ordinal());
            out.writeInt(payload.layout().strideBytes());

            out.writeInt(payload.layout().entries().size());
            for (VertexLayout.Entry entry : payload.layout().entries().values()) {
                out.writeInt(entry.key().semantic().ordinal());
                out.writeInt(entry.key().setIndex());
                out.writeInt(entry.format().ordinal());
                out.writeInt(entry.offsetBytes());
            }

            byte[] vertexBytes = toArray(payload.vertexBytes());
            out.writeInt(vertexBytes.length);
            out.write(vertexBytes);

            byte[] indexBytes = payload.indexBytes() == null ? new byte[0] : toArray(payload.indexBytes());
            out.writeInt(indexBytes.length);
            out.write(indexBytes);

            out.writeInt(payload.submeshes().size());
            for (PackedMesh.SubmeshRange submesh : payload.submeshes()) {
                out.writeInt(submesh.firstIndex());
                out.writeInt(submesh.indexCount());
                if (submesh.materialId() == null) {
                    out.writeBoolean(false);
                } else {
                    out.writeBoolean(true);
                    out.writeUTF(String.valueOf(submesh.materialId()));
                }
            }
        }
    }

    public static RuntimeGeometryPayload read(Path path) throws IOException {
        if (path == null) {
            throw new NullPointerException("path");
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid cache magic");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported cache version: " + version);
            }

            int vertexCount = in.readInt();
            int indexCount = in.readInt();
            int indexTypeOrdinal = in.readInt();
            int stride = in.readInt();

            int entryCount = in.readInt();
            LinkedHashMap<AttributeKey, VertexLayout.Entry> entries = new LinkedHashMap<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                AttributeSemantic semantic = AttributeSemantic.values()[in.readInt()];
                int setIndex = in.readInt();
                VertexFormat format = VertexFormat.values()[in.readInt()];
                int offset = in.readInt();
                AttributeKey key = new AttributeKey(semantic, setIndex);
                entries.put(key, new VertexLayout.Entry(key, format, offset));
            }

            int vertexByteCount = in.readInt();
            byte[] vertexBytes = in.readNBytes(vertexByteCount);
            if (vertexBytes.length != vertexByteCount) {
                throw new IOException("Unexpected EOF in vertex payload");
            }

            int indexByteCount = in.readInt();
            byte[] indexBytesRaw = in.readNBytes(indexByteCount);
            if (indexBytesRaw.length != indexByteCount) {
                throw new IOException("Unexpected EOF in index payload");
            }

            int submeshCount = in.readInt();
            List<PackedMesh.SubmeshRange> submeshes = new ArrayList<>(submeshCount);
            for (int i = 0; i < submeshCount; i++) {
                int first = in.readInt();
                int count = in.readInt();
                Object material = in.readBoolean() ? in.readUTF() : null;
                submeshes.add(new PackedMesh.SubmeshRange(first, count, material));
            }

            PackedMesh.IndexType indexType =
                indexTypeOrdinal < 0 ? null : PackedMesh.IndexType.values()[indexTypeOrdinal];
            ByteBuffer vertexBuffer = ByteBuffer.wrap(vertexBytes).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
            ByteBuffer indexBuffer = indexBytesRaw.length == 0
                ? null
                : ByteBuffer.wrap(indexBytesRaw).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer();
            VertexLayout layout = new VertexLayout(stride, entries);

            return new RuntimeGeometryPayload(
                layout,
                vertexCount,
                vertexBuffer,
                indexType,
                indexCount,
                indexBuffer,
                submeshes
            );
        }
    }

    private static byte[] toArray(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        copy.position(0);
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
