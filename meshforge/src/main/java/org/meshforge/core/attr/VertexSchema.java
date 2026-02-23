package org.meshforge.core.attr;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Ordered declaration of vertex attributes and storage formats.
 * <p>
 * Schemas define the contract between loaders, ops, and packers.
 */
public final class VertexSchema {
    private final LinkedHashMap<AttributeKey, VertexFormat> formats;

    private VertexSchema(LinkedHashMap<AttributeKey, VertexFormat> formats) {
        this.formats = new LinkedHashMap<>(formats);
    }

    public boolean has(AttributeSemantic semantic, int setIndex) {
        return formats.containsKey(new AttributeKey(semantic, setIndex));
    }

    public VertexFormat format(AttributeSemantic semantic, int setIndex) {
        VertexFormat format = formats.get(new AttributeKey(semantic, setIndex));
        if (format == null) {
            throw new NoSuchElementException("Missing attribute: " + semantic + "[" + setIndex + "]");
        }
        return format;
    }

    public Set<Map.Entry<AttributeKey, VertexFormat>> entries() {
        return Collections.unmodifiableSet(formats.entrySet());
    }

    public int attributeCount() {
        return formats.size();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Opinionated standard lit schema for common PBR-style meshes.
     */
    public static VertexSchema standardLit() {
        return builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .build();
    }

    /**
     * Opinionated standard skinned schema for animation-ready meshes.
     */
    public static VertexSchema standardSkinned() {
        return builder()
            .add(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .add(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .add(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .add(AttributeSemantic.JOINTS, 0, VertexFormat.U16x4)
            .add(AttributeSemantic.WEIGHTS, 0, VertexFormat.F32x4)
            .build();
    }

    public static final class Builder {
        private final LinkedHashMap<AttributeKey, VertexFormat> map = new LinkedHashMap<>();

        /**
         * Adds an attribute using set index 0.
         */
        public Builder add(AttributeSemantic semantic, VertexFormat format) {
            return add(semantic, 0, format);
        }

        /**
         * Adds a semantic/set pair to the schema.
         */
        public Builder add(AttributeSemantic semantic, int setIndex, VertexFormat format) {
            if (semantic == null) {
                throw new NullPointerException("semantic");
            }
            if (format == null) {
                throw new NullPointerException("format");
            }
            AttributeKey key = new AttributeKey(semantic, setIndex);
            VertexFormat previous = map.putIfAbsent(key, format);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate attribute in schema: " + key + " (already " + previous + ")");
            }
            return this;
        }

        public VertexSchema build() {
            if (!map.containsKey(new AttributeKey(AttributeSemantic.POSITION, 0))) {
                throw new IllegalStateException("VertexSchema must include POSITION[0]");
            }
            return new VertexSchema(map);
        }
    }
}
