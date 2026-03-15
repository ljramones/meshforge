package org.dynamisengine.meshforge.loader;

import org.dynamisengine.meshforge.loader.gltf.read.MeshoptCompressionFilter;
import org.dynamisengine.meshforge.loader.gltf.read.MeshoptCompressionMode;
import org.dynamisengine.meshforge.loader.gltf.read.MeshoptDecodeRequest;
import org.dynamisengine.meshforge.loader.gltf.read.MeshoptDecodeResult;
import org.dynamisengine.meshforge.loader.gltf.read.MeshoptDecoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Package-private utility class for glTF/GLB buffer decoding and parsing.
 */
final class GltfBufferOps {
    static final int GLB_MAGIC = 0x46546C67;
    static final int GLB_VERSION_2 = 2;
    static final int GLB_CHUNK_JSON = 0x4E4F534A;
    static final int GLB_CHUNK_BIN = 0x004E4942;

    private GltfBufferOps() {
    }

    record GlbDocument(String json, byte[] binChunk) {
    }

    static List<byte[]> decodeBuffers(Map<String, Object> root, Path path, byte[] embeddedBin) throws IOException {
        List<Map<String, Object>> buffers = GltfMeshLoader.listOfObjects(root.get("buffers"), "buffers");
        List<byte[]> out = new ArrayList<>(buffers.size());
        for (int i = 0; i < buffers.size(); i++) {
            Map<String, Object> buffer = buffers.get(i);
            Object uriValue = buffer.get("uri");
            if (uriValue == null) {
                if (embeddedBin != null && i == 0) {
                    out.add(embeddedBin);
                    continue;
                }
                throw new IOException("buffers[" + i + "] is missing uri and no embedded BIN chunk is available");
            }
            String uri = GltfMeshLoader.asString(uriValue, "buffers[" + i + "].uri");
            if (!uri.startsWith("data:")) {
                throw new IOException("Only data URI buffers are supported for now: " + path);
            }
            int comma = uri.indexOf(',');
            if (comma < 0 || !uri.substring(0, comma).contains(";base64")) {
                throw new IOException("Unsupported data URI format in buffers[" + i + "]");
            }
            String payload = uri.substring(comma + 1);
            byte[] decoded;
            try {
                decoded = Base64.getDecoder().decode(payload);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Invalid base64 buffer payload in buffers[" + i + "]", ex);
            }
            out.add(decoded);
        }
        return out;
    }

    static GltfAccessorOps.ViewData resolveViewData(
        List<Map<String, Object>> bufferViews,
        List<byte[]> buffers,
        int viewIndex,
        MeshLoadOptions options
    ) throws IOException {
        if (viewIndex < 0 || viewIndex >= bufferViews.size()) {
            throw new IOException("bufferView index out of range: " + viewIndex);
        }
        Map<String, Object> view = bufferViews.get(viewIndex);
        int defaultStride = GltfMeshLoader.numberAsInt(view.get("byteStride"), "bufferView.byteStride", 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> exts = (Map<String, Object>) view.get("extensions");
        if (exts != null && exts.containsKey("KHR_meshopt_compression")) {
            if (!options.meshoptDecodeEnabled()) {
                throw new IOException("gltf/glb uses KHR_meshopt_compression but meshopt decode is disabled");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> meshopt = GltfMeshLoader.asObject(exts.get("KHR_meshopt_compression"), "KHR_meshopt_compression");
            int sourceBufferIndex = GltfMeshLoader.numberAsInt(meshopt.get("buffer"), "meshopt.buffer");
            int sourceOffset = GltfMeshLoader.numberAsInt(meshopt.get("byteOffset"), "meshopt.byteOffset", 0);
            int sourceLength = GltfMeshLoader.numberAsInt(meshopt.get("byteLength"), "meshopt.byteLength");
            int count = GltfMeshLoader.numberAsInt(meshopt.get("count"), "meshopt.count");
            int byteStride = GltfMeshLoader.numberAsInt(meshopt.get("byteStride"), "meshopt.byteStride");
            String modeRaw = GltfMeshLoader.asString(meshopt.get("mode"), "meshopt.mode");
            String filterRaw = GltfMeshLoader.asString(meshopt.get("filter"), "meshopt.filter");
            int decompressedSize = GltfMeshLoader.numberAsInt(view.get("byteLength"), "bufferView.byteLength");

            byte[] sourceBuffer = bufferAt(buffers, sourceBufferIndex);
            if (sourceOffset < 0 || sourceLength < 0 || sourceOffset + sourceLength > sourceBuffer.length) {
                throw new IOException("meshopt source range is out of bounds");
            }
            byte[] compressed = new byte[sourceLength];
            System.arraycopy(sourceBuffer, sourceOffset, compressed, 0, sourceLength);

            MeshoptDecodeResult decoded = MeshoptDecoder.decode(new MeshoptDecodeRequest(
                compressed,
                decompressedSize,
                count,
                byteStride,
                MeshoptCompressionMode.fromString(modeRaw),
                MeshoptCompressionFilter.fromString(filterRaw)
            ));
            return new GltfAccessorOps.ViewData(decoded.data(), decoded.byteStride());
        }

        int bufferIndex = GltfMeshLoader.numberAsInt(view.get("buffer"), "bufferView.buffer");
        int byteOffset = GltfMeshLoader.numberAsInt(view.get("byteOffset"), "bufferView.byteOffset", 0);
        int byteLength = GltfMeshLoader.numberAsInt(view.get("byteLength"), "bufferView.byteLength");
        byte[] source = bufferAt(buffers, bufferIndex);
        if (byteOffset < 0 || byteLength < 0 || byteOffset + byteLength > source.length) {
            throw new IOException("bufferView range is out of bounds");
        }
        byte[] dataOut = new byte[byteLength];
        System.arraycopy(source, byteOffset, dataOut, 0, byteLength);
        return new GltfAccessorOps.ViewData(dataOut, defaultStride);
    }

    static GlbDocument parseGlb(Path path, byte[] bytes) throws IOException {
        if (bytes.length < 20) {
            throw new IOException("Invalid GLB (too small): " + path);
        }
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = bb.getInt();
        int version = bb.getInt();
        int totalLength = bb.getInt();
        if (magic != GLB_MAGIC) {
            throw new IOException("Invalid GLB magic: " + path);
        }
        if (version != GLB_VERSION_2) {
            throw new IOException("Unsupported GLB version " + version + ": " + path);
        }
        if (totalLength != bytes.length) {
            throw new IOException("GLB length mismatch: header=" + totalLength + " actual=" + bytes.length);
        }

        byte[] jsonChunk = null;
        byte[] binChunk = null;
        while (bb.remaining() >= 8) {
            int chunkLength = bb.getInt();
            int chunkType = bb.getInt();
            if (chunkLength < 0 || chunkLength > bb.remaining()) {
                throw new IOException("Invalid GLB chunk length: " + chunkLength);
            }
            byte[] chunkData = new byte[chunkLength];
            bb.get(chunkData);
            if (chunkType == GLB_CHUNK_JSON && jsonChunk == null) {
                jsonChunk = chunkData;
            } else if (chunkType == GLB_CHUNK_BIN && binChunk == null) {
                binChunk = chunkData;
            }
        }
        if (jsonChunk == null) {
            throw new IOException("GLB missing JSON chunk: " + path);
        }
        String json = decodeJsonChunk(jsonChunk);
        return new GlbDocument(json, binChunk);
    }

    static String decodeJsonChunk(byte[] jsonChunk) {
        int end = jsonChunk.length;
        while (end > 0) {
            int b = jsonChunk[end - 1] & 0xFF;
            if (b == 0 || b == 0x20 || b == 0x09 || b == 0x0A || b == 0x0D) {
                end--;
            } else {
                break;
            }
        }
        return new String(jsonChunk, 0, end, StandardCharsets.UTF_8);
    }

    static byte[] bufferAt(List<byte[]> buffers, int index) throws IOException {
        if (index < 0 || index >= buffers.size()) {
            throw new IOException("buffer index out of range: " + index);
        }
        return buffers.get(index);
    }
}
