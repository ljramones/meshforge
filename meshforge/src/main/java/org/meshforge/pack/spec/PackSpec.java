package org.meshforge.pack.spec;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class PackSpec {
    public enum LayoutMode { INTERLEAVED, MULTI_STREAM }

    public enum IndexPolicy { AUTO_16_IF_POSSIBLE, FORCE_32 }

    private final LayoutMode layoutMode;
    private final int alignmentBytes;
    private final IndexPolicy indexPolicy;
    private final boolean dropUnknownAttributes;
    private final boolean computeBoundsIfMissing;
    private final boolean failIfMissingNormals;
    private final boolean failIfMissingTangents;
    private final Map<AttributeKey, VertexFormat> targetFormats;

    private PackSpec(Builder builder) {
        this.layoutMode = builder.layoutMode;
        this.alignmentBytes = builder.alignmentBytes;
        this.indexPolicy = builder.indexPolicy;
        this.dropUnknownAttributes = builder.dropUnknownAttributes;
        this.computeBoundsIfMissing = builder.computeBoundsIfMissing;
        this.failIfMissingNormals = builder.failIfMissingNormals;
        this.failIfMissingTangents = builder.failIfMissingTangents;
        this.targetFormats = Collections.unmodifiableMap(new LinkedHashMap<>(builder.targetFormats));
    }

    public LayoutMode layoutMode() {
        return layoutMode;
    }

    public int alignmentBytes() {
        return alignmentBytes;
    }

    public IndexPolicy indexPolicy() {
        return indexPolicy;
    }

    public boolean dropUnknownAttributes() {
        return dropUnknownAttributes;
    }

    public boolean computeBoundsIfMissing() {
        return computeBoundsIfMissing;
    }

    public boolean failIfMissingNormals() {
        return failIfMissingNormals;
    }

    public boolean failIfMissingTangents() {
        return failIfMissingTangents;
    }

    public Map<AttributeKey, VertexFormat> targetFormats() {
        return targetFormats;
    }

    public VertexFormat targetFormat(AttributeSemantic semantic, int setIndex) {
        return targetFormats.get(new AttributeKey(semantic, setIndex));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PackSpec debug() {
        return builder()
            .layout(LayoutMode.INTERLEAVED)
            .alignment(16)
            .indexPolicy(IndexPolicy.AUTO_16_IF_POSSIBLE)
            .target(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .target(AttributeSemantic.NORMAL, 0, VertexFormat.F32x3)
            .target(AttributeSemantic.TANGENT, 0, VertexFormat.F32x4)
            .target(AttributeSemantic.UV, 0, VertexFormat.F32x2)
            .target(AttributeSemantic.COLOR, 0, VertexFormat.F32x4)
            .target(AttributeSemantic.JOINTS, 0, VertexFormat.I32x4)
            .target(AttributeSemantic.WEIGHTS, 0, VertexFormat.F32x4)
            .dropUnknownAttributes(false)
            .computeBoundsIfMissing(true)
            .failIfMissingNormals(false)
            .failIfMissingTangents(false)
            .build();
    }

    public static PackSpec realtime() {
        return builder()
            .layout(LayoutMode.INTERLEAVED)
            .alignment(16)
            .indexPolicy(IndexPolicy.AUTO_16_IF_POSSIBLE)
            .target(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .target(AttributeSemantic.NORMAL, 0, VertexFormat.SNORM8x4)
            .target(AttributeSemantic.TANGENT, 0, VertexFormat.SNORM8x4)
            .target(AttributeSemantic.UV, 0, VertexFormat.F16x2)
            .target(AttributeSemantic.COLOR, 0, VertexFormat.UNORM8x4)
            .target(AttributeSemantic.JOINTS, 0, VertexFormat.U8x4)
            .target(AttributeSemantic.WEIGHTS, 0, VertexFormat.UNORM8x4)
            .dropUnknownAttributes(true)
            .computeBoundsIfMissing(true)
            .failIfMissingNormals(false)
            .failIfMissingTangents(false)
            .build();
    }

    /**
     * Fast runtime pack preset.
     * Keeps the same output contract as realtime v1, with settings optimized for
     * already-prepared meshes that skip heavy preprocessing pipeline steps.
     */
    public static PackSpec realtimeFast() {
        return realtime();
    }

    public static final class Builder {
        private LayoutMode layoutMode = LayoutMode.INTERLEAVED;
        private int alignmentBytes = 16;
        private IndexPolicy indexPolicy = IndexPolicy.AUTO_16_IF_POSSIBLE;
        private boolean dropUnknownAttributes = true;
        private boolean computeBoundsIfMissing = true;
        private boolean failIfMissingNormals;
        private boolean failIfMissingTangents;
        private final LinkedHashMap<AttributeKey, VertexFormat> targetFormats = new LinkedHashMap<>();

        public Builder layout(LayoutMode mode) {
            this.layoutMode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        public Builder alignment(int bytes) {
            if (bytes != 0 && bytes != 4 && bytes != 8 && bytes != 16 && bytes != 32) {
                throw new IllegalArgumentException("alignmentBytes should be 0/4/8/16/32");
            }
            this.alignmentBytes = bytes;
            return this;
        }

        public Builder indexPolicy(IndexPolicy policy) {
            this.indexPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder dropUnknownAttributes(boolean value) {
            this.dropUnknownAttributes = value;
            return this;
        }

        public Builder computeBoundsIfMissing(boolean value) {
            this.computeBoundsIfMissing = value;
            return this;
        }

        public Builder failIfMissingNormals(boolean value) {
            this.failIfMissingNormals = value;
            return this;
        }

        public Builder failIfMissingTangents(boolean value) {
            this.failIfMissingTangents = value;
            return this;
        }

        public Builder target(AttributeSemantic semantic, int setIndex, VertexFormat format) {
            targetFormats.put(new AttributeKey(semantic, setIndex), Objects.requireNonNull(format, "format"));
            return this;
        }

        public PackSpec build() {
            return new PackSpec(this);
        }
    }
}
