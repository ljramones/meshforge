package org.meshforge.core.mesh;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.attr.VertexSchema;
import org.meshforge.core.bounds.Boundsf;
import org.meshforge.core.topology.Topology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Editable authoring-time mesh representation.
 * <p>
 * A {@code MeshData} instance owns topology, schema-backed vertex attributes,
 * optional index data, submesh partitions, optional morph targets, and optional
 * cached bounds.
 */
public final class MeshData {
    private static final int MAX_SUBMESH_COUNT = 1_000_000;

    private final Topology topology;
    private VertexSchema schema;
    private int vertexCount;
    private final Map<AttributeKey, AttributeStorage> attributes = new LinkedHashMap<>();
    private int[] indices;
    private final List<Submesh> submeshes = new ArrayList<>();
    private final List<MorphTarget> morphTargets = new ArrayList<>();
    private Boundsf bounds;

    /**
     * Creates a triangle mesh with the provided schema and vertex count.
     */
    public MeshData(VertexSchema schema, int vertexCount) {
        this(Topology.TRIANGLES, schema, vertexCount, null, List.of(), List.of());
    }

    /**
     * Creates a mesh with explicit topology, indices, and submesh ranges.
     */
    public MeshData(
        Topology topology,
        VertexSchema schema,
        int vertexCount,
        int[] indices,
        List<Submesh> submeshes
    ) {
        this(topology, schema, vertexCount, indices, submeshes, List.of());
    }

    /**
     * Creates a mesh with explicit topology, indices, submeshes, and morph targets.
     */
    public MeshData(
        Topology topology,
        VertexSchema schema,
        int vertexCount,
        int[] indices,
        List<Submesh> submeshes,
        List<MorphTarget> morphTargets
    ) {
        if (topology == null) {
            throw new NullPointerException("topology");
        }
        if (schema == null) {
            throw new NullPointerException("schema");
        }
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount < 0");
        }
        if (submeshes == null) {
            throw new NullPointerException("submeshes");
        }
        if (morphTargets == null) {
            throw new NullPointerException("morphTargets");
        }
        if (submeshes.size() > MAX_SUBMESH_COUNT) {
            throw new IllegalArgumentException(
                "submeshCount exceeds limit: " + submeshes.size() + " > " + MAX_SUBMESH_COUNT);
        }
        this.topology = topology;
        this.schema = schema;
        this.vertexCount = vertexCount;
        this.indices = indices == null ? null : indices.clone();
        this.submeshes.addAll(submeshes);
        setMorphTargets(morphTargets);

        for (Map.Entry<AttributeKey, VertexFormat> entry : schema.entries()) {
            AttributeKey key = entry.getKey();
            VertexFormat format = entry.getValue();
            attributes.put(key, AttributeStorage.allocate(key, format, vertexCount));
        }
    }

    public VertexSchema schema() {
        return schema;
    }

    public Topology topology() {
        return topology;
    }

    public int vertexCount() {
        return vertexCount;
    }

    /**
     * In-place vertex-count resizing is intentionally unsupported.
     * Create a new {@code MeshData} with the desired vertex count instead.
     */
    public void setVertexCount(int newVertexCount) {
        if (newVertexCount != this.vertexCount) {
            throw new UnsupportedOperationException(
                "Resizing MeshData in place is unsupported; create a new MeshData with desired vertexCount");
        }
    }

    public int[] indicesOrNull() {
        return indices;
    }

    /**
     * Sets optional triangle indices. Input is defensively copied.
     */
    public void setIndices(int[] newIndices) {
        this.indices = newIndices == null ? null : newIndices.clone();
    }

    public List<Submesh> submeshes() {
        return Collections.unmodifiableList(submeshes);
    }

    /**
     * Replaces submesh partitions with the provided list.
     */
    public void setSubmeshes(List<Submesh> newSubmeshes) {
        if (newSubmeshes == null) {
            throw new NullPointerException("newSubmeshes");
        }
        if (newSubmeshes.size() > MAX_SUBMESH_COUNT) {
            throw new IllegalArgumentException(
                "submeshCount exceeds limit: " + newSubmeshes.size() + " > " + MAX_SUBMESH_COUNT);
        }
        submeshes.clear();
        submeshes.addAll(newSubmeshes);
    }

    public Boundsf boundsOrNull() {
        return bounds;
    }

    /**
     * Sets optional cached bounds.
     */
    public void setBounds(Boundsf newBounds) {
        this.bounds = newBounds;
    }

    public List<MorphTarget> morphTargets() {
        return Collections.unmodifiableList(morphTargets);
    }

    /**
     * Replaces morph targets after validating delta lengths against {@code vertexCount}.
     */
    public void setMorphTargets(List<MorphTarget> newMorphTargets) {
        if (newMorphTargets == null) {
            throw new NullPointerException("newMorphTargets");
        }
        morphTargets.clear();
        for (MorphTarget target : newMorphTargets) {
            if (target == null) {
                throw new NullPointerException("morphTarget");
            }
            validateMorphTarget(target);
            morphTargets.add(target);
        }
    }

    public void addMorphTarget(MorphTarget target) {
        if (target == null) {
            throw new NullPointerException("target");
        }
        validateMorphTarget(target);
        morphTargets.add(target);
    }

    public boolean has(AttributeSemantic semantic, int setIndex) {
        return attributes.containsKey(new AttributeKey(semantic, setIndex));
    }

    /**
     * Returns a mutable view for the requested schema attribute.
     *
     * @throws NoSuchElementException if the attribute is absent
     */
    public VertexAttributeView attribute(AttributeSemantic semantic, int setIndex) {
        AttributeKey key = new AttributeKey(semantic, setIndex);
        AttributeStorage storage = attributes.get(key);
        if (storage == null) {
            throw new NoSuchElementException("Missing attribute: " + key);
        }
        return storage.view();
    }

    public VertexAttributeView addAttribute(AttributeSemantic semantic, int setIndex, VertexFormat format) {
        AttributeKey key = new AttributeKey(semantic, setIndex);
        if (attributes.containsKey(key)) {
            throw new IllegalStateException("Attribute already exists: " + key);
        }
        AttributeStorage storage = AttributeStorage.allocate(key, format, vertexCount);
        attributes.put(key, storage);
        schema = appendSchemaEntry(schema, key, format);
        return storage.view();
    }

    public Map<AttributeKey, VertexFormat> attributeFormats() {
        Map<AttributeKey, VertexFormat> result = new LinkedHashMap<>();
        for (Map.Entry<AttributeKey, AttributeStorage> entry : attributes.entrySet()) {
            result.put(entry.getKey(), entry.getValue().format);
        }
        return Collections.unmodifiableMap(result);
    }

    private static VertexSchema appendSchemaEntry(VertexSchema base, AttributeKey key, VertexFormat format) {
        VertexSchema.Builder builder = VertexSchema.builder();
        for (Map.Entry<AttributeKey, VertexFormat> entry : base.entries()) {
            AttributeKey existing = entry.getKey();
            builder.add(existing.semantic(), existing.setIndex(), entry.getValue());
        }
        builder.add(key.semantic(), key.setIndex(), format);
        return builder.build();
    }

    private void validateMorphTarget(MorphTarget target) {
        final int required;
        try {
            required = Math.multiplyExact(vertexCount, 3);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                "vertexCount is too large for morph target deltas: " + vertexCount, ex);
        }
        if (target.positionDeltas().length != required) {
            throw new IllegalArgumentException(
                "Morph target '" + target.name() + "' POSITION delta length must be " + required);
        }
        float[] normals = target.normalDeltas();
        if (normals != null && normals.length != required) {
            throw new IllegalArgumentException(
                "Morph target '" + target.name() + "' NORMAL delta length must be " + required);
        }
        float[] tangents = target.tangentDeltas();
        if (tangents != null && tangents.length != required) {
            throw new IllegalArgumentException(
                "Morph target '" + target.name() + "' TANGENT delta length must be " + required);
        }
    }
}
