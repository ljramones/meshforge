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

public final class MeshData {
    private final Topology topology;
    private final VertexSchema schema;
    private int vertexCount;
    private final Map<AttributeKey, AttributeStorage> attributes = new HashMap<>();
    private int[] indices;
    private final List<Submesh> submeshes = new ArrayList<>();
    private Boundsf bounds;

    public MeshData(VertexSchema schema, int vertexCount) {
        this(Topology.TRIANGLES, schema, vertexCount, null, List.of());
    }

    public MeshData(
        Topology topology,
        VertexSchema schema,
        int vertexCount,
        int[] indices,
        List<Submesh> submeshes
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
        this.topology = topology;
        this.schema = schema;
        this.vertexCount = vertexCount;
        this.indices = indices == null ? null : indices.clone();
        this.submeshes.addAll(submeshes);

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

    public void setVertexCount(int newVertexCount) {
        if (newVertexCount < 0) {
            throw new IllegalArgumentException("newVertexCount < 0");
        }
        this.vertexCount = newVertexCount;
    }

    public int[] indicesOrNull() {
        return indices;
    }

    public void setIndices(int[] newIndices) {
        this.indices = newIndices == null ? null : newIndices.clone();
    }

    public List<Submesh> submeshes() {
        return Collections.unmodifiableList(submeshes);
    }

    public void setSubmeshes(List<Submesh> newSubmeshes) {
        if (newSubmeshes == null) {
            throw new NullPointerException("newSubmeshes");
        }
        submeshes.clear();
        submeshes.addAll(newSubmeshes);
    }

    public Boundsf boundsOrNull() {
        return bounds;
    }

    public void setBounds(Boundsf newBounds) {
        this.bounds = newBounds;
    }

    public boolean has(AttributeSemantic semantic, int setIndex) {
        return attributes.containsKey(new AttributeKey(semantic, setIndex));
    }

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
        return storage.view();
    }

    public Map<AttributeKey, VertexFormat> attributeFormats() {
        Map<AttributeKey, VertexFormat> result = new LinkedHashMap<>();
        for (Map.Entry<AttributeKey, AttributeStorage> entry : attributes.entrySet()) {
            result.put(entry.getKey(), entry.getValue().format);
        }
        return Collections.unmodifiableMap(result);
    }
}
