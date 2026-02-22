package org.meshforge.loader;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.core.topology.Topology;
import org.meshforge.loader.gltf.read.MeshoptCompressionFilter;
import org.meshforge.loader.gltf.read.MeshoptCompressionMode;
import org.meshforge.loader.gltf.read.MeshoptDecodeRequest;
import org.meshforge.loader.gltf.read.MeshoptDecodeResult;
import org.meshforge.loader.gltf.read.MeshoptDecoder;
import org.meshforge.loader.gltf.read.MiniJson;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal glTF loader supporting data URI buffers and meshopt-compressed bufferViews.
 */
public final class GltfMeshLoader implements MeshFileLoader {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int GLB_VERSION_2 = 2;
    private static final int GLB_CHUNK_JSON = 0x4E4F534A;
    private static final int GLB_CHUNK_BIN = 0x004E4942;

    private static final int COMPONENT_FLOAT = 5126;
    private static final int COMPONENT_UNSIGNED_INT = 5125;
    private static final int COMPONENT_UNSIGNED_SHORT = 5123;
    private static final int COMPONENT_UNSIGNED_BYTE = 5121;

    @Override
    public MeshData load(Path path) throws IOException {
        return load(path, MeshLoadOptions.defaults());
    }

    @Override
    public MeshData load(Path path, MeshLoadOptions options) throws IOException {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase();
        if (name.endsWith(".glb")) {
            return loadGlb(path, options == null ? MeshLoadOptions.defaults() : options);
        }
        if (!name.endsWith(".gltf")) {
            throw new IOException("Unsupported glTF extension: " + path);
        }
        return loadGltf(path, options == null ? MeshLoadOptions.defaults() : options);
    }

    private MeshData loadGltf(Path path, MeshLoadOptions options) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return loadFromJson(path, options, json, null);
    }

    private MeshData loadGlb(Path path, MeshLoadOptions options) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        GlbDocument glb = parseGlb(path, bytes);
        return loadFromJson(path, options, glb.json(), glb.binChunk());
    }

    private MeshData loadFromJson(Path path, MeshLoadOptions options, String json, byte[] embeddedBin) throws IOException {
        Map<String, Object> root;
        try {
            root = MiniJson.parseObject(json);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid glTF JSON: " + path + " (" + ex.getMessage() + ")", ex);
        }

        List<byte[]> buffers = decodeBuffers(root, path, embeddedBin);
        List<Map<String, Object>> bufferViews = listOfObjects(root.get("bufferViews"), "bufferViews");
        List<Map<String, Object>> accessors = listOfObjects(root.get("accessors"), "accessors");
        Map<String, Object> primitive = firstPrimitive(root);

        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = asObject(primitive.get("attributes"), "meshes[0].primitives[0].attributes");
        Integer positionAccessorIndex = numberAsInt(attrs.get("POSITION"), "POSITION accessor index");
        if (positionAccessorIndex == null) {
            throw new IOException("glTF primitive is missing POSITION attribute");
        }

        LinkedHashMap<AttributeSemantic, Integer> semantics = new LinkedHashMap<>();
        semantics.put(AttributeSemantic.POSITION, positionAccessorIndex);
        putIfPresent(attrs, "NORMAL", AttributeSemantic.NORMAL, semantics);
        putIfPresent(attrs, "TEXCOORD_0", AttributeSemantic.UV, semantics);

        VertexSchema.Builder schemaBuilder = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3);
        if (semantics.containsKey(AttributeSemantic.NORMAL)) {
            schemaBuilder.add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3);
        }
        if (semantics.containsKey(AttributeSemantic.UV)) {
            schemaBuilder.add(AttributeSemantic.UV, 0, VertexFormat.F32x2);
        }
        VertexSchema schema = schemaBuilder.build();

        AccessorData pos = readAccessor(accessors, bufferViews, buffers, positionAccessorIndex, options);
        int vertexCount = pos.count;
        MeshData mesh = new MeshData(Topology.TRIANGLES, schema, vertexCount, null, List.of());
        copyFloatAttribute(mesh, AttributeSemantic.POSITION, 0, pos, 3);

        if (semantics.containsKey(AttributeSemantic.NORMAL)) {
            AccessorData normals = readAccessor(
                accessors, bufferViews, buffers, semantics.get(AttributeSemantic.NORMAL), options);
            if (normals.count != vertexCount) {
                throw new IOException("NORMAL accessor count mismatch: " + normals.count + " != " + vertexCount);
            }
            copyFloatAttribute(mesh, AttributeSemantic.NORMAL, 0, normals, 3);
        }
        if (semantics.containsKey(AttributeSemantic.UV)) {
            AccessorData uv = readAccessor(accessors, bufferViews, buffers, semantics.get(AttributeSemantic.UV), options);
            if (uv.count != vertexCount) {
                throw new IOException("TEXCOORD_0 accessor count mismatch: " + uv.count + " != " + vertexCount);
            }
            copyFloatAttribute(mesh, AttributeSemantic.UV, 0, uv, 2);
        }

        Integer indicesAccessor = numberAsInt(primitive.get("indices"), "indices accessor");
        if (indicesAccessor != null) {
            AccessorData idx = readAccessor(accessors, bufferViews, buffers, indicesAccessor, options);
            int[] indices = new int[idx.count];
            for (int i = 0; i < idx.count; i++) {
                indices[i] = readUnsignedIntComponent(idx, i, 0);
            }
            mesh.setIndices(indices);
            mesh.setSubmeshes(List.of(new Submesh(0, indices.length, "default")));
        }
        return mesh;
    }

    private static void putIfPresent(
        Map<String, Object> attrs,
        String key,
        AttributeSemantic semantic,
        Map<AttributeSemantic, Integer> out
    ) throws IOException {
        Integer idx = numberAsInt(attrs.get(key), key + " accessor index");
        if (idx != null) {
            out.put(semantic, idx);
        }
    }

    private static void copyFloatAttribute(
        MeshData mesh,
        AttributeSemantic semantic,
        int set,
        AccessorData accessor,
        int expectedComponents
    ) throws IOException {
        if (accessor.components != expectedComponents) {
            throw new IOException(
                semantic + " accessor components mismatch: expected " + expectedComponents + " got " + accessor.components);
        }
        var view = mesh.attribute(semantic, set);
        for (int i = 0; i < accessor.count; i++) {
            for (int c = 0; c < expectedComponents; c++) {
                view.setFloat(i, c, readFloatComponent(accessor, i, c));
            }
        }
    }

    private static float readFloatComponent(AccessorData accessor, int index, int component) throws IOException {
        int off = accessor.byteOffset + index * accessor.byteStride + component * accessor.componentSize;
        if (off + accessor.componentSize > accessor.data.length) {
            throw new IOException("Accessor read out of bounds");
        }
        return switch (accessor.componentType) {
            case COMPONENT_FLOAT -> accessor.buffer.getFloat(off);
            default -> throw new IOException("Unsupported float accessor componentType: " + accessor.componentType);
        };
    }

    private static int readUnsignedIntComponent(AccessorData accessor, int index, int component) throws IOException {
        int off = accessor.byteOffset + index * accessor.byteStride + component * accessor.componentSize;
        if (off + accessor.componentSize > accessor.data.length) {
            throw new IOException("Index accessor read out of bounds");
        }
        return switch (accessor.componentType) {
            case COMPONENT_UNSIGNED_INT -> accessor.buffer.getInt(off);
            case COMPONENT_UNSIGNED_SHORT -> accessor.buffer.getShort(off) & 0xFFFF;
            case COMPONENT_UNSIGNED_BYTE -> accessor.buffer.get(off) & 0xFF;
            default -> throw new IOException("Unsupported index componentType: " + accessor.componentType);
        };
    }

    private static AccessorData readAccessor(
        List<Map<String, Object>> accessors,
        List<Map<String, Object>> bufferViews,
        List<byte[]> buffers,
        int accessorIndex,
        MeshLoadOptions options
    ) throws IOException {
        if (accessorIndex < 0 || accessorIndex >= accessors.size()) {
            throw new IOException("Accessor index out of range: " + accessorIndex);
        }
        Map<String, Object> accessor = accessors.get(accessorIndex);
        Integer count = numberAsInt(accessor.get("count"), "accessor.count");
        Integer componentType = numberAsInt(accessor.get("componentType"), "accessor.componentType");
        String type = asString(accessor.get("type"), "accessor.type");
        Integer viewIndex = numberAsInt(accessor.get("bufferView"), "accessor.bufferView");
        int accessorByteOffset = numberAsInt(accessor.get("byteOffset"), "accessor.byteOffset", 0);
        if (count == null || componentType == null || viewIndex == null) {
            throw new IOException("Accessor is missing required fields");
        }

        ViewData viewData = resolveViewData(bufferViews, buffers, viewIndex, options);
        int componentCount = componentCount(type);
        int compSize = componentSize(componentType);
        int packedStride = componentCount * compSize;
        int stride = viewData.byteStride > 0 ? viewData.byteStride : packedStride;
        return new AccessorData(
            viewData.data,
            ByteBuffer.wrap(viewData.data).order(ByteOrder.LITTLE_ENDIAN),
            accessorByteOffset,
            count,
            componentType,
            componentCount,
            compSize,
            stride
        );
    }

    private static ViewData resolveViewData(
        List<Map<String, Object>> bufferViews,
        List<byte[]> buffers,
        int viewIndex,
        MeshLoadOptions options
    ) throws IOException {
        if (viewIndex < 0 || viewIndex >= bufferViews.size()) {
            throw new IOException("bufferView index out of range: " + viewIndex);
        }
        Map<String, Object> view = bufferViews.get(viewIndex);
        int defaultStride = numberAsInt(view.get("byteStride"), "bufferView.byteStride", 0);

        @SuppressWarnings("unchecked")
        Map<String, Object> exts = (Map<String, Object>) view.get("extensions");
        if (exts != null && exts.containsKey("KHR_meshopt_compression")) {
            if (!options.meshoptDecodeEnabled()) {
                throw new IOException("gltf/glb uses KHR_meshopt_compression but meshopt decode is disabled");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> meshopt = asObject(exts.get("KHR_meshopt_compression"), "KHR_meshopt_compression");
            int sourceBufferIndex = numberAsInt(meshopt.get("buffer"), "meshopt.buffer");
            int sourceOffset = numberAsInt(meshopt.get("byteOffset"), "meshopt.byteOffset", 0);
            int sourceLength = numberAsInt(meshopt.get("byteLength"), "meshopt.byteLength");
            int count = numberAsInt(meshopt.get("count"), "meshopt.count");
            int byteStride = numberAsInt(meshopt.get("byteStride"), "meshopt.byteStride");
            String modeRaw = asString(meshopt.get("mode"), "meshopt.mode");
            String filterRaw = asString(meshopt.get("filter"), "meshopt.filter");
            int decompressedSize = numberAsInt(view.get("byteLength"), "bufferView.byteLength");

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
            return new ViewData(decoded.data(), decoded.byteStride());
        }

        int bufferIndex = numberAsInt(view.get("buffer"), "bufferView.buffer");
        int byteOffset = numberAsInt(view.get("byteOffset"), "bufferView.byteOffset", 0);
        int byteLength = numberAsInt(view.get("byteLength"), "bufferView.byteLength");
        byte[] source = bufferAt(buffers, bufferIndex);
        if (byteOffset < 0 || byteLength < 0 || byteOffset + byteLength > source.length) {
            throw new IOException("bufferView range is out of bounds");
        }
        byte[] out = new byte[byteLength];
        System.arraycopy(source, byteOffset, out, 0, byteLength);
        return new ViewData(out, defaultStride);
    }

    private static List<byte[]> decodeBuffers(Map<String, Object> root, Path path, byte[] embeddedBin) throws IOException {
        List<Map<String, Object>> buffers = listOfObjects(root.get("buffers"), "buffers");
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
            String uri = asString(uriValue, "buffers[" + i + "].uri");
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

    private static GlbDocument parseGlb(Path path, byte[] bytes) throws IOException {
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

    private static String decodeJsonChunk(byte[] jsonChunk) {
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

    private static Map<String, Object> firstPrimitive(Map<String, Object> root) throws IOException {
        List<Map<String, Object>> meshes = listOfObjects(root.get("meshes"), "meshes");
        if (meshes.isEmpty()) {
            throw new IOException("glTF JSON missing meshes[0]");
        }
        List<Map<String, Object>> primitives = listOfObjects(meshes.get(0).get("primitives"), "meshes[0].primitives");
        if (primitives.isEmpty()) {
            throw new IOException("glTF JSON missing meshes[0].primitives[0]");
        }
        return primitives.get(0);
    }

    private static byte[] bufferAt(List<byte[]> buffers, int index) throws IOException {
        if (index < 0 || index >= buffers.size()) {
            throw new IOException("buffer index out of range: " + index);
        }
        return buffers.get(index);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfObjects(Object value, String label) throws IOException {
        if (!(value instanceof List<?> list)) {
            throw new IOException("Expected JSON array for " + label);
        }
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(asObject(item, label + " item"));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String label) throws IOException {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IOException("Expected JSON object for " + label);
        }
        return (Map<String, Object>) map;
    }

    private static String asString(Object value, String label) throws IOException {
        if (!(value instanceof String s)) {
            throw new IOException("Expected string for " + label);
        }
        return s;
    }

    private static Integer numberAsInt(Object value, String label) throws IOException {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number n)) {
            throw new IOException("Expected number for " + label);
        }
        return n.intValue();
    }

    private static int numberAsInt(Object value, String label, int defaultValue) throws IOException {
        Integer v = numberAsInt(value, label);
        return v == null ? defaultValue : v;
    }

    private static int componentCount(String type) throws IOException {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            default -> throw new IOException("Unsupported accessor type: " + type);
        };
    }

    private static int componentSize(int componentType) throws IOException {
        return switch (componentType) {
            case COMPONENT_UNSIGNED_BYTE -> 1;
            case COMPONENT_UNSIGNED_SHORT -> 2;
            case COMPONENT_UNSIGNED_INT, COMPONENT_FLOAT -> 4;
            default -> throw new IOException("Unsupported accessor componentType: " + componentType);
        };
    }

    private record ViewData(byte[] data, int byteStride) {
    }

    private record AccessorData(
        byte[] data,
        ByteBuffer buffer,
        int byteOffset,
        int count,
        int componentType,
        int components,
        int componentSize,
        int byteStride
    ) {
    }

    private record GlbDocument(String json, byte[] binChunk) {
    }
}
