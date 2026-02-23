package org.meshforge.loader;

import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.MorphTarget;
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
        PrimitiveRef primitiveRef = firstPrimitive(root);
        Map<String, Object> primitive = primitiveRef.primitive();
        // v1 scope: only meshes[0].primitives[0] is imported. Morph targets are loaded from this primitive only.

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
        putIfPresent(attrs, "JOINTS_0", AttributeSemantic.JOINTS, semantics);
        putIfPresent(attrs, "WEIGHTS_0", AttributeSemantic.WEIGHTS, semantics);

        VertexSchema.Builder schemaBuilder = VertexSchema.builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3);
        if (semantics.containsKey(AttributeSemantic.NORMAL)) {
            schemaBuilder.add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3);
        }
        if (semantics.containsKey(AttributeSemantic.UV)) {
            schemaBuilder.add(AttributeSemantic.UV, 0, VertexFormat.F32x2);
        }
        if (semantics.containsKey(AttributeSemantic.JOINTS)) {
            schemaBuilder.add(AttributeSemantic.JOINTS, 0, VertexFormat.I32x4);
        }
        if (semantics.containsKey(AttributeSemantic.WEIGHTS)) {
            schemaBuilder.add(AttributeSemantic.WEIGHTS, 0, VertexFormat.F32x4);
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
        Integer skinJointCount = null;
        Integer resolvedSkinIndex = null;
        if (semantics.containsKey(AttributeSemantic.JOINTS)) {
            resolvedSkinIndex = resolveSkinIndex(root, primitiveRef);
            if (resolvedSkinIndex == null) {
                throw new IOException("JOINTS_0 present but no skin reference could be resolved for primitive");
            }
            skinJointCount = skinJointCount(root, resolvedSkinIndex);
        }
        if (semantics.containsKey(AttributeSemantic.JOINTS)) {
            AccessorData joints = readAccessor(accessors, bufferViews, buffers, semantics.get(AttributeSemantic.JOINTS), options);
            if (joints.count != vertexCount) {
                throw new IOException("JOINTS_0 accessor count mismatch: " + joints.count + " != " + vertexCount);
            }
            copyJointAttribute(mesh, joints, skinJointCount);
        }
        if (semantics.containsKey(AttributeSemantic.WEIGHTS)) {
            AccessorData weights = readAccessor(accessors, bufferViews, buffers, semantics.get(AttributeSemantic.WEIGHTS), options);
            if (weights.count != vertexCount) {
                throw new IOException("WEIGHTS_0 accessor count mismatch: " + weights.count + " != " + vertexCount);
            }
            copyWeightsAttribute(mesh, weights);
        }
        mesh.setMorphTargets(readMorphTargets(root, primitiveRef, primitive, accessors, bufferViews, buffers, options, vertexCount));

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

    private static List<MorphTarget> readMorphTargets(
        Map<String, Object> root,
        PrimitiveRef primitiveRef,
        Map<String, Object> primitive,
        List<Map<String, Object>> accessors,
        List<Map<String, Object>> bufferViews,
        List<byte[]> buffers,
        MeshLoadOptions options,
        int vertexCount
    ) throws IOException {
        Object rawTargets = primitive.get("targets");
        if (rawTargets == null) {
            return List.of();
        }
        List<Map<String, Object>> targets = listOfObjects(rawTargets, "meshes[0].primitives[0].targets");
        if (targets.isEmpty()) {
            return List.of();
        }

        List<String> targetNames = morphTargetNames(root, primitiveRef.meshIndex());
        List<MorphTarget> out = new ArrayList<>(targets.size());
        for (int i = 0; i < targets.size(); i++) {
            Map<String, Object> target = targets.get(i);
            Integer posAccessor = numberAsInt(target.get("POSITION"), "targets[" + i + "].POSITION");
            if (posAccessor == null) {
                throw new IOException("Morph target " + i + " is missing POSITION deltas");
            }
            float[] pos = readMorphDelta(accessors, bufferViews, buffers, posAccessor, options, vertexCount, "POSITION", i);

            Integer normalAccessor = numberAsInt(target.get("NORMAL"), "targets[" + i + "].NORMAL");
            float[] nrm = normalAccessor == null
                ? null
                : readMorphDelta(accessors, bufferViews, buffers, normalAccessor, options, vertexCount, "NORMAL", i);

            Integer tangentAccessor = numberAsInt(target.get("TANGENT"), "targets[" + i + "].TANGENT");
            float[] tan = tangentAccessor == null
                ? null
                : readMorphDelta(accessors, bufferViews, buffers, tangentAccessor, options, vertexCount, "TANGENT", i);

            String name = i < targetNames.size() ? targetNames.get(i) : "target_" + i;
            out.add(new MorphTarget(name, pos, nrm, tan));
        }
        return out;
    }

    private static float[] readMorphDelta(
        List<Map<String, Object>> accessors,
        List<Map<String, Object>> bufferViews,
        List<byte[]> buffers,
        int accessorIndex,
        MeshLoadOptions options,
        int vertexCount,
        String semantic,
        int targetIndex
    ) throws IOException {
        AccessorData accessor = readAccessor(accessors, bufferViews, buffers, accessorIndex, options);
        if (accessor.count != vertexCount) {
            throw new IOException(
                "Morph target " + targetIndex + " " + semantic + " count mismatch: " + accessor.count + " != " + vertexCount);
        }
        if (accessor.components != 3 || accessor.componentType != COMPONENT_FLOAT) {
            throw new IOException(
                "Morph target " + targetIndex + " " + semantic + " must be VEC3/FLOAT");
        }
        final float[] out;
        try {
            out = new float[Math.multiplyExact(vertexCount, 3)];
        } catch (ArithmeticException ex) {
            throw new IOException(
                "Morph target " + targetIndex + " " + semantic + " allocation overflow for vertexCount=" + vertexCount,
                ex
            );
        }
        for (int i = 0; i < vertexCount; i++) {
            int o = i * 3;
            out[o] = readFloatComponent(accessor, i, 0);
            out[o + 1] = readFloatComponent(accessor, i, 1);
            out[o + 2] = readFloatComponent(accessor, i, 2);
        }
        return out;
    }

    private static List<String> morphTargetNames(Map<String, Object> root, int meshIndex) throws IOException {
        Object meshesValue = root.get("meshes");
        if (meshesValue == null) {
            return List.of();
        }
        List<Map<String, Object>> meshes = listOfObjects(meshesValue, "meshes");
        if (meshIndex < 0 || meshIndex >= meshes.size()) {
            return List.of();
        }
        Map<String, Object> mesh = meshes.get(meshIndex);
        Object extrasValue = mesh.get("extras");
        if (extrasValue == null) {
            return List.of();
        }
        Map<String, Object> extras = asObject(extrasValue, "meshes[" + meshIndex + "].extras");
        Object namesValue = extras.get("targetNames");
        if (namesValue == null) {
            return List.of();
        }
        if (!(namesValue instanceof List<?> rawNames)) {
            throw new IOException("Expected JSON array for meshes[" + meshIndex + "].extras.targetNames");
        }
        List<String> names = new ArrayList<>(rawNames.size());
        for (Object value : rawNames) {
            if (!(value instanceof String s) || s.isBlank()) {
                throw new IOException("Invalid morph target name in meshes[" + meshIndex + "].extras.targetNames");
            }
            names.add(s);
        }
        return names;
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
        if (off < 0 || off + accessor.componentSize > accessor.data.length) {
            throw new IOException("Accessor read out of bounds");
        }
        return switch (accessor.componentType) {
            case COMPONENT_FLOAT -> accessor.buffer.getFloat(off);
            default -> throw new IOException("Unsupported float accessor componentType: " + accessor.componentType);
        };
    }

    private static int readUnsignedIntComponent(AccessorData accessor, int index, int component) throws IOException {
        int off = accessor.byteOffset + index * accessor.byteStride + component * accessor.componentSize;
        if (off < 0 || off + accessor.componentSize > accessor.data.length) {
            throw new IOException("Index accessor read out of bounds");
        }
        return switch (accessor.componentType) {
            case COMPONENT_UNSIGNED_INT -> accessor.buffer.getInt(off);
            case COMPONENT_UNSIGNED_SHORT -> accessor.buffer.getShort(off) & 0xFFFF;
            case COMPONENT_UNSIGNED_BYTE -> accessor.buffer.get(off) & 0xFF;
            default -> throw new IOException("Unsupported index componentType: " + accessor.componentType);
        };
    }

    private static void copyJointAttribute(
        MeshData mesh,
        AccessorData accessor,
        Integer skinJointCount
    ) throws IOException {
        if (accessor.components != 4) {
            throw new IOException("JOINTS_0 accessor components mismatch: expected 4 got " + accessor.components);
        }
        var view = mesh.attribute(AttributeSemantic.JOINTS, 0);
        for (int i = 0; i < accessor.count; i++) {
            for (int c = 0; c < 4; c++) {
                int joint = readUnsignedIntComponent(accessor, i, c);
                if (skinJointCount != null && joint >= skinJointCount) {
                    throw new IOException(
                        "JOINTS_0 value out of range at vertex " + i + " component " + c
                            + ": " + joint + " (skin jointCount=" + skinJointCount + ")"
                    );
                }
                view.setInt(i, c, joint);
            }
        }
    }

    private static void copyWeightsAttribute(MeshData mesh, AccessorData accessor) throws IOException {
        if (accessor.components != 4) {
            throw new IOException("WEIGHTS_0 accessor components mismatch: expected 4 got " + accessor.components);
        }
        var view = mesh.attribute(AttributeSemantic.WEIGHTS, 0);
        for (int i = 0; i < accessor.count; i++) {
            float w0 = readWeightComponent(accessor, i, 0);
            float w1 = readWeightComponent(accessor, i, 1);
            float w2 = readWeightComponent(accessor, i, 2);
            float w3 = readWeightComponent(accessor, i, 3);
            float sum = w0 + w1 + w2 + w3;
            if (sum > 1.0e-8f) {
                float inv = 1.0f / sum;
                w0 *= inv;
                w1 *= inv;
                w2 *= inv;
                w3 *= inv;
            } else {
                w0 = 1.0f;
                w1 = 0.0f;
                w2 = 0.0f;
                w3 = 0.0f;
            }
            view.setFloat(i, 0, w0);
            view.setFloat(i, 1, w1);
            view.setFloat(i, 2, w2);
            view.setFloat(i, 3, w3);
        }
    }

    private static float readWeightComponent(AccessorData accessor, int index, int component) throws IOException {
        int off = accessor.byteOffset + index * accessor.byteStride + component * accessor.componentSize;
        if (off < 0 || off + accessor.componentSize > accessor.data.length) {
            throw new IOException("Accessor read out of bounds");
        }
        return switch (accessor.componentType) {
            case COMPONENT_FLOAT -> accessor.buffer.getFloat(off);
            case COMPONENT_UNSIGNED_BYTE -> {
                int v = accessor.buffer.get(off) & 0xFF;
                yield accessor.normalized ? v / 255.0f : (float) v;
            }
            case COMPONENT_UNSIGNED_SHORT -> {
                int v = accessor.buffer.getShort(off) & 0xFFFF;
                yield accessor.normalized ? v / 65535.0f : (float) v;
            }
            default -> throw new IOException("Unsupported WEIGHTS_0 componentType: " + accessor.componentType);
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
        if (count < 0) {
            throw new IOException("Accessor count must be >= 0");
        }
        if (accessorByteOffset < 0) {
            throw new IOException("Accessor byteOffset must be >= 0");
        }

        ViewData viewData = resolveViewData(bufferViews, buffers, viewIndex, options);
        int componentCount = componentCount(type);
        int compSize = componentSize(componentType);
        int packedStride = componentCount * compSize;
        int stride = viewData.byteStride > 0 ? viewData.byteStride : packedStride;
        if (stride < packedStride) {
            throw new IOException("Accessor stride is smaller than packed element size");
        }
        ensureAccessorRangeFits(viewData.data.length, accessorByteOffset, count, stride, packedStride);
        boolean normalized = Boolean.TRUE.equals(accessor.get("normalized"));
        return new AccessorData(
            viewData.data,
            ByteBuffer.wrap(viewData.data).order(ByteOrder.LITTLE_ENDIAN),
            accessorByteOffset,
            count,
            componentType,
            componentCount,
            compSize,
            stride,
            normalized
        );
    }

    private static Integer resolveSkinIndex(Map<String, Object> root, PrimitiveRef primitiveRef) throws IOException {
        Integer primitiveSkin = numberAsInt(primitiveRef.primitive().get("skin"), "primitive.skin");
        if (primitiveSkin != null) {
            return primitiveSkin;
        }

        Object nodesValue = root.get("nodes");
        if (nodesValue == null) {
            return null;
        }
        List<Map<String, Object>> nodes = listOfObjects(nodesValue, "nodes");
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> node = nodes.get(i);
            Integer nodeMesh = numberAsInt(node.get("mesh"), "nodes[" + i + "].mesh");
            if (nodeMesh == null || nodeMesh != primitiveRef.meshIndex()) {
                continue;
            }
            Integer nodeSkin = numberAsInt(node.get("skin"), "nodes[" + i + "].skin");
            if (nodeSkin != null) {
                return nodeSkin;
            }
        }
        return null;
    }

    private static Integer skinJointCount(Map<String, Object> root, int skinIndex) throws IOException {
        Object skinsValue = root.get("skins");
        if (skinsValue == null) {
            return null;
        }
        List<Map<String, Object>> skins = listOfObjects(skinsValue, "skins");
        if (skinIndex < 0 || skinIndex >= skins.size()) {
            throw new IOException("skin index out of range: " + skinIndex);
        }
        if (skins.isEmpty()) {
            return null;
        }
        Object jointsValue = skins.get(skinIndex).get("joints");
        if (jointsValue == null) {
            return null;
        }
        if (!(jointsValue instanceof List<?> list)) {
            throw new IOException("Expected JSON array for skins[" + skinIndex + "].joints");
        }
        return list.size();
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

    private static PrimitiveRef firstPrimitive(Map<String, Object> root) throws IOException {
        List<Map<String, Object>> meshes = listOfObjects(root.get("meshes"), "meshes");
        if (meshes.isEmpty()) {
            throw new IOException("glTF JSON missing meshes[0]");
        }
        List<Map<String, Object>> primitives = listOfObjects(meshes.get(0).get("primitives"), "meshes[0].primitives");
        if (primitives.isEmpty()) {
            throw new IOException("glTF JSON missing meshes[0].primitives[0]");
        }
        return new PrimitiveRef(0, 0, primitives.get(0));
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

    private static void ensureAccessorRangeFits(
        int bufferLength,
        int accessorByteOffset,
        int count,
        int stride,
        int packedStride
    ) throws IOException {
        if (count == 0) {
            if (accessorByteOffset > bufferLength) {
                throw new IOException("Accessor byteOffset is out of bounds");
            }
            return;
        }
        long lastOffset = (long) accessorByteOffset + (long) (count - 1) * stride;
        long requiredEnd = lastOffset + packedStride;
        if (requiredEnd > bufferLength) {
            throw new IOException("Accessor range is out of bounds");
        }
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
        int byteStride,
        boolean normalized
    ) {
    }

    private record GlbDocument(String json, byte[] binChunk) {
    }

    private record PrimitiveRef(int meshIndex, int primitiveIndex, Map<String, Object> primitive) {
    }
}
