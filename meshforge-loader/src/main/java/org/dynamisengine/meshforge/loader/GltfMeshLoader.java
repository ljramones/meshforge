package org.dynamisengine.meshforge.loader;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.attr.VertexSchema;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.MorphTarget;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.core.topology.Topology;
import org.dynamisengine.meshforge.loader.gltf.read.MiniJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal glTF loader supporting data URI buffers and meshopt-compressed bufferViews.
 */
public final class GltfMeshLoader implements MeshFileLoader {

    @Override
    /**
     * Loads load.
     * @param path parameter value
     * @return resulting value
     */
    public MeshData load(Path path) throws IOException {
        return load(path, MeshLoadOptions.defaults());
    }

    @Override
    /**
     * Loads load.
     * @param path parameter value
     * @param options parameter value
     * @return resulting value
     */
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
        GltfBufferOps.GlbDocument glb = GltfBufferOps.parseGlb(path, bytes);
        return loadFromJson(path, options, glb.json(), glb.binChunk());
    }

    private MeshData loadFromJson(Path path, MeshLoadOptions options, String json, byte[] embeddedBin) throws IOException {
        Map<String, Object> root;
        try {
            root = MiniJson.parseObject(json);
        } catch (IllegalArgumentException ex) {
            throw new IOException("Invalid glTF JSON: " + path + " (" + ex.getMessage() + ")", ex);
        }

        List<byte[]> buffers = GltfBufferOps.decodeBuffers(root, path, embeddedBin);
        List<Map<String, Object>> bufferViews = listOfObjects(root.get("bufferViews"), "bufferViews");
        List<Map<String, Object>> accessors = listOfObjects(root.get("accessors"), "accessors");
        PrimitiveRef primitiveRef = firstPrimitive(root);
        Map<String, Object> primitive = primitiveRef.primitive();

        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = asObject(primitive.get("attributes"), "meshes[0].primitives[0].attributes");
        Integer positionAccessorIndex = numberAsInt(attrs.get("POSITION"), "POSITION accessor index");
        if (positionAccessorIndex == null) {
            throw new IOException("glTF primitive is missing POSITION attribute");
        }

        LinkedHashMap<AttributeSemantic, Integer> semantics = new LinkedHashMap<>();
        semantics.put(AttributeSemantic.POSITION, positionAccessorIndex);
        GltfAccessorOps.putIfPresent(attrs, "NORMAL", AttributeSemantic.NORMAL, semantics);
        GltfAccessorOps.putIfPresent(attrs, "TEXCOORD_0", AttributeSemantic.UV, semantics);
        GltfAccessorOps.putIfPresent(attrs, "JOINTS_0", AttributeSemantic.JOINTS, semantics);
        GltfAccessorOps.putIfPresent(attrs, "WEIGHTS_0", AttributeSemantic.WEIGHTS, semantics);

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

        GltfAccessorOps.AccessorData pos = GltfAccessorOps.readAccessor(accessors, bufferViews, buffers, positionAccessorIndex, options);
        int vertexCount = pos.count();
        MeshData mesh = new MeshData(Topology.TRIANGLES, schema, vertexCount, null, List.of());
        GltfAccessorOps.copyFloatAttribute(mesh, AttributeSemantic.POSITION, 0, pos, 3);

        if (semantics.containsKey(AttributeSemantic.NORMAL)) {
            GltfAccessorOps.AccessorData normals = GltfAccessorOps.readAccessor(
                accessors, bufferViews, buffers, semantics.get(AttributeSemantic.NORMAL), options);
            if (normals.count() != vertexCount) {
                throw new IOException("NORMAL accessor count mismatch: " + normals.count() + " != " + vertexCount);
            }
            GltfAccessorOps.copyFloatAttribute(mesh, AttributeSemantic.NORMAL, 0, normals, 3);
        }
        if (semantics.containsKey(AttributeSemantic.UV)) {
            GltfAccessorOps.AccessorData uv = GltfAccessorOps.readAccessor(accessors, bufferViews, buffers, semantics.get(AttributeSemantic.UV), options);
            if (uv.count() != vertexCount) {
                throw new IOException("TEXCOORD_0 accessor count mismatch: " + uv.count() + " != " + vertexCount);
            }
            GltfAccessorOps.copyFloatAttribute(mesh, AttributeSemantic.UV, 0, uv, 2);
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
            GltfAccessorOps.AccessorData joints = GltfAccessorOps.readAccessor(accessors, bufferViews, buffers, semantics.get(AttributeSemantic.JOINTS), options);
            if (joints.count() != vertexCount) {
                throw new IOException("JOINTS_0 accessor count mismatch: " + joints.count() + " != " + vertexCount);
            }
            GltfAccessorOps.copyJointAttribute(mesh, joints, skinJointCount);
        }
        if (semantics.containsKey(AttributeSemantic.WEIGHTS)) {
            GltfAccessorOps.AccessorData weights = GltfAccessorOps.readAccessor(accessors, bufferViews, buffers, semantics.get(AttributeSemantic.WEIGHTS), options);
            if (weights.count() != vertexCount) {
                throw new IOException("WEIGHTS_0 accessor count mismatch: " + weights.count() + " != " + vertexCount);
            }
            GltfAccessorOps.copyWeightsAttribute(mesh, weights);
        }
        mesh.setMorphTargets(readMorphTargets(root, primitiveRef, primitive, accessors, bufferViews, buffers, options, vertexCount));

        Integer indicesAccessor = numberAsInt(primitive.get("indices"), "indices accessor");
        if (indicesAccessor != null) {
            GltfAccessorOps.AccessorData idx = GltfAccessorOps.readAccessor(accessors, bufferViews, buffers, indicesAccessor, options);
            int[] indices = new int[idx.count()];
            for (int i = 0; i < idx.count(); i++) {
                indices[i] = GltfAccessorOps.readUnsignedIntComponent(idx, i, 0);
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
            float[] posDeltas = readMorphDelta(accessors, bufferViews, buffers, posAccessor, options, vertexCount, "POSITION", i);

            Integer normalAccessor = numberAsInt(target.get("NORMAL"), "targets[" + i + "].NORMAL");
            float[] nrm = normalAccessor == null
                ? null
                : readMorphDelta(accessors, bufferViews, buffers, normalAccessor, options, vertexCount, "NORMAL", i);

            Integer tangentAccessor = numberAsInt(target.get("TANGENT"), "targets[" + i + "].TANGENT");
            float[] tan = tangentAccessor == null
                ? null
                : readMorphDelta(accessors, bufferViews, buffers, tangentAccessor, options, vertexCount, "TANGENT", i);

            String targetName = i < targetNames.size() ? targetNames.get(i) : "target_" + i;
            out.add(new MorphTarget(targetName, posDeltas, nrm, tan));
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
        GltfAccessorOps.AccessorData accessor = GltfAccessorOps.readAccessor(accessors, bufferViews, buffers, accessorIndex, options);
        if (accessor.count() != vertexCount) {
            throw new IOException(
                "Morph target " + targetIndex + " " + semantic + " count mismatch: " + accessor.count() + " != " + vertexCount);
        }
        if (accessor.components() != 3 || accessor.componentType() != GltfAccessorOps.COMPONENT_FLOAT) {
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
            out[o] = GltfAccessorOps.readFloatComponent(accessor, i, 0);
            out[o + 1] = GltfAccessorOps.readFloatComponent(accessor, i, 1);
            out[o + 2] = GltfAccessorOps.readFloatComponent(accessor, i, 2);
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

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> listOfObjects(Object value, String label) throws IOException {
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
    static Map<String, Object> asObject(Object value, String label) throws IOException {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IOException("Expected JSON object for " + label);
        }
        return (Map<String, Object>) map;
    }

    static String asString(Object value, String label) throws IOException {
        if (!(value instanceof String s)) {
            throw new IOException("Expected string for " + label);
        }
        return s;
    }

    static Integer numberAsInt(Object value, String label) throws IOException {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number n)) {
            throw new IOException("Expected number for " + label);
        }
        return n.intValue();
    }

    static int numberAsInt(Object value, String label, int defaultValue) throws IOException {
        Integer v = numberAsInt(value, label);
        return v == null ? defaultValue : v;
    }

    record PrimitiveRef(int meshIndex, int primitiveIndex, Map<String, Object> primitive) {
    }
}
