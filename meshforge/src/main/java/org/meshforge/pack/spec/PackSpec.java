package org.meshforge.pack.spec;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexFormat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable packing contract used by {@code MeshPacker}.
 * <p>
 * A {@code PackSpec} controls layout mode, index policy, target attribute
 * formats, optional meshlet generation, and validation behavior.
 */
public final class PackSpec {
    /**
     * Vertex buffer layout strategy.
     */
    public enum LayoutMode { INTERLEAVED, MULTI_STREAM }

    /**
     * Packed index width policy.
     */
    public enum IndexPolicy { AUTO_16_IF_POSSIBLE, FORCE_32 }

    /**
     * Runtime normal encoding mode.
     */
    public enum NormalPacking { SNORM8x4, OCTA_SNORM16x2 }

    private final LayoutMode layoutMode;
    private final int alignmentBytes;
    private final IndexPolicy indexPolicy;
    private final boolean dropUnknownAttributes;
    private final boolean computeBoundsIfMissing;
    private final boolean failIfMissingNormals;
    private final boolean failIfMissingTangents;
    private final boolean meshletsEnabled;
    private final int maxMeshletVertices;
    private final int maxMeshletTriangles;
    private final NormalPacking normalPacking;
    private final Map<AttributeKey, VertexFormat> targetFormats;

    private PackSpec(Builder builder) {
        this.layoutMode = builder.layoutMode;
        this.alignmentBytes = builder.alignmentBytes;
        this.indexPolicy = builder.indexPolicy;
        this.dropUnknownAttributes = builder.dropUnknownAttributes;
        this.computeBoundsIfMissing = builder.computeBoundsIfMissing;
        this.failIfMissingNormals = builder.failIfMissingNormals;
        this.failIfMissingTangents = builder.failIfMissingTangents;
        this.meshletsEnabled = builder.meshletsEnabled;
        this.maxMeshletVertices = builder.maxMeshletVertices;
        this.maxMeshletTriangles = builder.maxMeshletTriangles;
        this.normalPacking = builder.normalPacking;
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

    public boolean meshletsEnabled() {
        return meshletsEnabled;
    }

    public int maxMeshletVertices() {
        return maxMeshletVertices;
    }

    public int maxMeshletTriangles() {
        return maxMeshletTriangles;
    }

    public NormalPacking normalPacking() {
        return normalPacking;
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
            .normalPacking(NormalPacking.SNORM8x4)
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

    /**
     * Minimal runtime pack preset.
     * Position-only output for quick-load or tooling scenarios.
     */
    public static PackSpec realtimeMinimal() {
        return builder()
            .layout(LayoutMode.INTERLEAVED)
            .alignment(16)
            .indexPolicy(IndexPolicy.AUTO_16_IF_POSSIBLE)
            .target(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .dropUnknownAttributes(true)
            .computeBoundsIfMissing(true)
            .failIfMissingNormals(false)
            .failIfMissingTangents(false)
            .build();
    }

    public static PackSpec realtimeWithMeshlets() {
        return builder()
            .layout(LayoutMode.INTERLEAVED)
            .alignment(16)
            .indexPolicy(IndexPolicy.AUTO_16_IF_POSSIBLE)
            .target(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .normalPacking(NormalPacking.SNORM8x4)
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
            .meshletsEnabled(true)
            .maxMeshletVertices(128)
            .maxMeshletTriangles(64)
            .build();
    }

    public static PackSpec realtimeWithOctaNormals() {
        return builder()
            .layout(LayoutMode.INTERLEAVED)
            .alignment(16)
            .indexPolicy(IndexPolicy.AUTO_16_IF_POSSIBLE)
            .target(AttributeSemantic.POSITION, 0, VertexFormat.F32x3)
            .normalPacking(NormalPacking.OCTA_SNORM16x2)
            .target(AttributeSemantic.NORMAL, 0, VertexFormat.OCTA_SNORM16x2)
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

    public static final class Builder {
        private LayoutMode layoutMode = LayoutMode.INTERLEAVED;
        private int alignmentBytes = 16;
        private IndexPolicy indexPolicy = IndexPolicy.AUTO_16_IF_POSSIBLE;
        private boolean dropUnknownAttributes = true;
        private boolean computeBoundsIfMissing = true;
        private boolean failIfMissingNormals;
        private boolean failIfMissingTangents;
        private boolean meshletsEnabled;
        private int maxMeshletVertices = 128;
        private int maxMeshletTriangles = 64;
        private NormalPacking normalPacking = NormalPacking.SNORM8x4;
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

        public Builder meshletsEnabled(boolean value) {
            this.meshletsEnabled = value;
            return this;
        }

        public Builder maxMeshletVertices(int value) {
            if (value < 3 || value > 256) {
                throw new IllegalArgumentException("maxMeshletVertices must be in [3, 256]");
            }
            this.maxMeshletVertices = value;
            return this;
        }

        public Builder maxMeshletTriangles(int value) {
            if (value < 1 || value > 256) {
                throw new IllegalArgumentException("maxMeshletTriangles must be in [1, 256]");
            }
            this.maxMeshletTriangles = value;
            return this;
        }

        public Builder normalPacking(NormalPacking mode) {
            this.normalPacking = Objects.requireNonNull(mode, "mode");
            if (mode == NormalPacking.OCTA_SNORM16x2) {
                targetFormats.put(new AttributeKey(AttributeSemantic.NORMAL, 0), VertexFormat.OCTA_SNORM16x2);
            } else {
                targetFormats.put(new AttributeKey(AttributeSemantic.NORMAL, 0), VertexFormat.SNORM8x4);
            }
            return this;
        }

        public Builder target(AttributeSemantic semantic, int setIndex, VertexFormat format) {
            targetFormats.put(new AttributeKey(semantic, setIndex), Objects.requireNonNull(format, "format"));
            if (semantic == AttributeSemantic.NORMAL && setIndex == 0) {
                if (format == VertexFormat.OCTA_SNORM16x2) {
                    normalPacking = NormalPacking.OCTA_SNORM16x2;
                } else if (format == VertexFormat.SNORM8x4) {
                    normalPacking = NormalPacking.SNORM8x4;
                }
            }
            return this;
        }

        public PackSpec build() {
            return new PackSpec(this);
        }
    }
}
