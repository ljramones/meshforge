package org.dynamisengine.meshforge.pack.spec;

import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexFormat;

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

    /**
     * Returns layoutMode.
     * @return resulting value
     */
    public LayoutMode layoutMode() {
        return layoutMode;
    }

    /**
     * Returns alignmentBytes.
     * @return resulting value
     */
    public int alignmentBytes() {
        return alignmentBytes;
    }

    /**
     * Returns indexPolicy.
     * @return resulting value
     */
    public IndexPolicy indexPolicy() {
        return indexPolicy;
    }

    /**
     * Executes dropUnknownAttributes.
     * @return resulting value
     */
    public boolean dropUnknownAttributes() {
        return dropUnknownAttributes;
    }

    /**
     * Executes computeBoundsIfMissing.
     * @return resulting value
     */
    public boolean computeBoundsIfMissing() {
        return computeBoundsIfMissing;
    }

    /**
     * Executes failIfMissingNormals.
     * @return resulting value
     */
    public boolean failIfMissingNormals() {
        return failIfMissingNormals;
    }

    /**
     * Executes failIfMissingTangents.
     * @return resulting value
     */
    public boolean failIfMissingTangents() {
        return failIfMissingTangents;
    }

    /**
     * Returns targetFormats.
     * @return resulting value
     */
    public Map<AttributeKey, VertexFormat> targetFormats() {
        return targetFormats;
    }

    /**
     * Executes meshletsEnabled.
     * @return resulting value
     */
    public boolean meshletsEnabled() {
        return meshletsEnabled;
    }

    /**
     * Returns maxMeshletVertices.
     * @return resulting value
     */
    public int maxMeshletVertices() {
        return maxMeshletVertices;
    }

    /**
     * Returns maxMeshletTriangles.
     * @return resulting value
     */
    public int maxMeshletTriangles() {
        return maxMeshletTriangles;
    }

    /**
     * Returns normalPacking.
     * @return resulting value
     */
    public NormalPacking normalPacking() {
        return normalPacking;
    }

    /**
     * Executes targetFormat.
     * @param semantic parameter value
     * @param setIndex parameter value
     * @return resulting value
     */
    public VertexFormat targetFormat(AttributeSemantic semantic, int setIndex) {
        return targetFormats.get(new AttributeKey(semantic, setIndex));
    }

    /**
     * Creates builder.
     * @return resulting value
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns debug.
     * @return resulting value
     */
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

    /**
     * Returns realtime.
     * @return resulting value
     */
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
     *
     * @return fast runtime pack preset
     */
    public static PackSpec realtimeFast() {
        return realtime();
    }

    /**
     * Minimal runtime pack preset.
     * Position-only output for quick-load or tooling scenarios.
     *
     * @return minimal runtime pack preset
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

    /**
     * Returns realtimeWithMeshlets.
     * @return resulting value
     */
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

    /**
     * Returns realtimeWithOctaNormals.
     * @return resulting value
     */
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

    /**
     * Fluent builder for {@link PackSpec}.
     */
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

        /**
         * Creates a builder with realtime-oriented defaults.
         */
        public Builder() {
        }

        /**
         * Sets the packed vertex layout mode.
         *
         * @param mode layout mode
         * @return builder
         */
        public Builder layout(LayoutMode mode) {
            this.layoutMode = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /**
         * Sets packed vertex alignment in bytes.
         *
         * @param bytes alignment in bytes (0, 4, 8, 16, or 32)
         * @return builder
         */
        public Builder alignment(int bytes) {
            if (bytes != 0 && bytes != 4 && bytes != 8 && bytes != 16 && bytes != 32) {
                throw new IllegalArgumentException("alignmentBytes should be 0/4/8/16/32");
            }
            this.alignmentBytes = bytes;
            return this;
        }

        /**
         * Sets index width policy.
         *
         * @param policy index policy
         * @return builder
         */
        public Builder indexPolicy(IndexPolicy policy) {
            this.indexPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        /**
         * Sets whether unknown attributes are removed during packing.
         *
         * @param value {@code true} to drop unknown attributes
         * @return builder
         */
        public Builder dropUnknownAttributes(boolean value) {
            this.dropUnknownAttributes = value;
            return this;
        }

        /**
         * Sets whether bounds should be generated when absent.
         *
         * @param value {@code true} to compute bounds when missing
         * @return builder
         */
        public Builder computeBoundsIfMissing(boolean value) {
            this.computeBoundsIfMissing = value;
            return this;
        }

        /**
         * Sets whether packing should fail when normals are missing.
         *
         * @param value {@code true} to require normals
         * @return builder
         */
        public Builder failIfMissingNormals(boolean value) {
            this.failIfMissingNormals = value;
            return this;
        }

        /**
         * Sets whether packing should fail when tangents are missing.
         *
         * @param value {@code true} to require tangents
         * @return builder
         */
        public Builder failIfMissingTangents(boolean value) {
            this.failIfMissingTangents = value;
            return this;
        }

        /**
         * Enables or disables meshlet generation.
         *
         * @param value {@code true} to generate meshlets
         * @return builder
         */
        public Builder meshletsEnabled(boolean value) {
            this.meshletsEnabled = value;
            return this;
        }

        /**
         * Sets meshlet vertex limit.
         *
         * @param value max vertices per meshlet
         * @return builder
         */
        public Builder maxMeshletVertices(int value) {
            if (value < 3 || value > 256) {
                throw new IllegalArgumentException("maxMeshletVertices must be in [3, 256]");
            }
            this.maxMeshletVertices = value;
            return this;
        }

        /**
         * Sets meshlet triangle limit.
         *
         * @param value max triangles per meshlet
         * @return builder
         */
        public Builder maxMeshletTriangles(int value) {
            if (value < 1 || value > 256) {
                throw new IllegalArgumentException("maxMeshletTriangles must be in [1, 256]");
            }
            this.maxMeshletTriangles = value;
            return this;
        }

        /**
         * Sets normal packing mode.
         *
         * @param mode normal packing mode
         * @return builder
         */
        public Builder normalPacking(NormalPacking mode) {
            this.normalPacking = Objects.requireNonNull(mode, "mode");
            if (mode == NormalPacking.OCTA_SNORM16x2) {
                targetFormats.put(new AttributeKey(AttributeSemantic.NORMAL, 0), VertexFormat.OCTA_SNORM16x2);
            } else {
                targetFormats.put(new AttributeKey(AttributeSemantic.NORMAL, 0), VertexFormat.SNORM8x4);
            }
            return this;
        }

        /**
         * Sets the target format for one attribute.
         *
         * @param semantic attribute semantic
         * @param setIndex attribute set index
         * @param format target vertex format
         * @return builder
         */
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

        /**
         * Builds an immutable {@link PackSpec}.
         *
         * @return pack specification
         */
        public PackSpec build() {
            return new PackSpec(this);
        }
    }
}
