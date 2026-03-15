package org.dynamisengine.meshforge.pack.packer;

import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.ops.optimize.MeshletClusters;
import org.dynamisengine.meshforge.pack.buffer.MeshletBufferView;
import org.dynamisengine.meshforge.pack.buffer.MeshletBuffers;
import org.dynamisengine.meshforge.pack.buffer.PackedMesh;
import org.dynamisengine.meshforge.pack.layout.VertexLayout;
import org.dynamisengine.meshforge.pack.simd.SimdNormalPacker;
import org.dynamisengine.meshforge.pack.spec.PackSpec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * Offline mesh packing: builds immutable {@link PackedMesh} results with optional profiling.
 * For runtime (allocation-free) paths, see {@link RuntimeMeshPacker}.
 */
public final class MeshPacker {
    private static final int PROFILE_SAMPLE_RATE = 1024;
    private static final Logger LOGGER = Logger.getLogger(MeshPacker.class.getName());
    static final AttributeKey POSITION_0 = new AttributeKey(AttributeSemantic.POSITION, 0);
    static final AttributeKey NORMAL_0 = new AttributeKey(AttributeSemantic.NORMAL, 0);
    static final AttributeKey TANGENT_0 = new AttributeKey(AttributeSemantic.TANGENT, 0);
    static final AttributeKey UV_0 = new AttributeKey(AttributeSemantic.UV, 0);
    static final AttributeKey COLOR_0 = new AttributeKey(AttributeSemantic.COLOR, 0);
    static final AttributeKey JOINTS_0 = new AttributeKey(AttributeSemantic.JOINTS, 0);
    static final AttributeKey WEIGHTS_0 = new AttributeKey(AttributeSemantic.WEIGHTS, 0);
    static final int MASK_NORMAL = 1 << 0;
    static final int MASK_TANGENT = 1 << 1;
    static final int MASK_UV = 1 << 2;
    static final int MASK_COLOR = 1 << 3;
    static final int MASK_JOINTS = 1 << 4;
    static final int MASK_WEIGHTS = 1 << 5;

    private MeshPacker() {
    }

    /**
     * Stage-level runtime packing profile for reusable/runtime paths.
     */
    public static final class RuntimePackProfile {
        long totalNs;
        long vertexPayloadNs;
        long indexPayloadNs;
        long submeshMetadataNs;

        /**
         * Creates an empty profile.
         */
        public RuntimePackProfile() {
        }

        /**
         * Clears all counters.
         */
        public void reset() {
            totalNs = 0L;
            vertexPayloadNs = 0L;
            indexPayloadNs = 0L;
            submeshMetadataNs = 0L;
        }

        /**
         * Returns totalNs.
         * @return resulting value
         */
        public long totalNs() {
            return totalNs;
        }

        /**
         * Returns vertexPayloadNs.
         * @return resulting value
         */
        public long vertexPayloadNs() {
            return vertexPayloadNs;
        }

        /**
         * Returns indexPayloadNs.
         * @return resulting value
         */
        public long indexPayloadNs() {
            return indexPayloadNs;
        }

        /**
         * Returns submeshMetadataNs.
         * @return resulting value
         */
        public long submeshMetadataNs() {
            return submeshMetadataNs;
        }
    }

    /**
     * Small reusable pool for runtime pack workspaces.
     * Callers can own one pool per worker/thread domain to amortize workspace lifetime.
     */
    public static final class RuntimePackWorkspacePool {
        private final ArrayDeque<RuntimePackWorkspace> free;
        private final int maxRetained;

        public RuntimePackWorkspacePool(int maxRetained) {
            if (maxRetained <= 0) {
                throw new IllegalArgumentException("maxRetained must be > 0");
            }
            this.maxRetained = maxRetained;
            this.free = new ArrayDeque<>(Math.min(maxRetained, 64));
        }

        public synchronized RuntimePackWorkspace acquire() {
            RuntimePackWorkspace workspace = free.pollFirst();
            return workspace == null ? new RuntimePackWorkspace() : workspace;
        }

        public synchronized void release(RuntimePackWorkspace workspace) {
            if (workspace == null) {
                return;
            }
            if (free.size() < maxRetained) {
                free.addFirst(workspace);
            }
        }

        public synchronized int retainedCount() {
            return free.size();
        }
    }

    /**
     * Executes pack.
     * @param mesh parameter value
     * @param spec parameter value
     * @return resulting value
     */
    public static PackedMesh pack(MeshData mesh, PackSpec spec) {
        return pack(mesh, spec, null);
    }

    /**
     * Executes pack.
     * @param mesh parameter value
     * @param spec parameter value
     * @param profile parameter value
     * @return resulting value
     */
    public static PackedMesh pack(MeshData mesh, PackSpec spec, PackProfile profile) {
        if (profile != null) {
            profile.reset();
        }
        long totalStart = profile == null ? 0L : System.nanoTime();
        if (spec.layoutMode() != PackSpec.LayoutMode.INTERLEAVED) {
            throw new UnsupportedOperationException("v1 supports INTERLEAVED only");
        }
        if (!mesh.morphTargets().isEmpty()) {
            LOGGER.warning("Morph targets are present on MeshData but are not packed in v1; packing base mesh only.");
        }

        long resolveStart = profile == null ? 0L : System.nanoTime();
        VertexAttributeView position = require(mesh, POSITION_0);
        VertexAttributeView normal = optional(mesh, NORMAL_0);
        VertexAttributeView tangent = optional(mesh, TANGENT_0);
        VertexAttributeView uv0 = optional(mesh, UV_0);
        VertexAttributeView color0 = optional(mesh, COLOR_0);
        VertexAttributeView joints0 = optional(mesh, JOINTS_0);
        VertexAttributeView weights0 = optional(mesh, WEIGHTS_0);

        if (spec.failIfMissingNormals() && normal == null) {
            throw new IllegalStateException("Missing NORMAL[0] but PackSpec requires it");
        }
        if (spec.failIfMissingTangents() && tangent == null) {
            throw new IllegalStateException("Missing TANGENT[0] but PackSpec requires it");
        }
        if (profile != null) {
            profile.setResolveAttributesNs(System.nanoTime() - resolveStart);
        }

        long layoutStart = profile == null ? 0L : System.nanoTime();
        int offset = 0;
        LinkedHashMap<AttributeKey, VertexLayout.Entry> entries = new LinkedHashMap<>();

        offset = add(entries, offset, POSITION_0, spec.targetFormat(POSITION_0));
        if (normal != null) {
            offset = add(entries, offset, NORMAL_0, spec.targetFormat(NORMAL_0));
        }
        if (tangent != null) {
            offset = add(entries, offset, TANGENT_0, spec.targetFormat(TANGENT_0));
        }
        if (uv0 != null) {
            offset = add(entries, offset, UV_0, spec.targetFormat(UV_0));
        }
        if (color0 != null) {
            offset = add(entries, offset, COLOR_0, spec.targetFormat(COLOR_0));
        }
        if (joints0 != null) {
            offset = add(entries, offset, JOINTS_0, spec.targetFormat(JOINTS_0));
        }
        if (weights0 != null) {
            offset = add(entries, offset, WEIGHTS_0, spec.targetFormat(WEIGHTS_0));
        }

        int stride = align(offset, spec.alignmentBytes());
        VertexLayout layout = new VertexLayout(stride, entries);

        int vertexCount = mesh.vertexCount();
        if (profile != null) {
            profile.setLayoutNs(System.nanoTime() - layoutStart);
            profile.setVertexCount(vertexCount);
            profile.setStrideBytes(stride);
        }

        long vertexWriteStart = profile == null ? 0L : System.nanoTime();
        boolean profileEnabled = profile != null;
        long positionWriteNs = 0L;
        long normalPackNs = 0L;
        long tangentPackNs = 0L;
        long uvPackNs = 0L;
        long colorPackNs = 0L;
        long skinPackNs = 0L;
        final ByteBuffer vertexBuffer;
        try {
            int vertexBufferBytes = Math.multiplyExact(vertexCount, stride);
            vertexBuffer = ByteBuffer.allocateDirect(vertexBufferBytes).order(ByteOrder.LITTLE_ENDIAN);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException(
                "Vertex buffer size overflow: vertexCount=" + vertexCount + ", stride=" + stride, ex);
        }

        float[] positionData = requireFloat(position, "POSITION");
        float[] normalData = normal == null ? null : normal.rawFloatArrayOrNull();
        float[] tangentData = tangent == null ? null : tangent.rawFloatArrayOrNull();
        float[] uvData = uv0 == null ? null : uv0.rawFloatArrayOrNull();
        float[] colorData = color0 == null ? null : color0.rawFloatArrayOrNull();

        int[] jointsData = joints0 == null ? null : joints0.rawIntArrayOrNull();
        float[] weightsData = weights0 == null ? null : weights0.rawFloatArrayOrNull();

        VertexLayout.Entry posEntry = layout.entry(POSITION_0);
        VertexLayout.Entry normalEntry = layout.entry(NORMAL_0);
        VertexLayout.Entry tangentEntry = layout.entry(TANGENT_0);
        VertexLayout.Entry uvEntry = layout.entry(UV_0);
        VertexLayout.Entry colorEntry = layout.entry(COLOR_0);
        VertexLayout.Entry jointsEntry = layout.entry(JOINTS_0);
        VertexLayout.Entry weightsEntry = layout.entry(WEIGHTS_0);

        int posOff = posEntry == null ? -1 : posEntry.offsetBytes();
        int normalOff = normalEntry == null ? -1 : normalEntry.offsetBytes();
        int tangentOff = tangentEntry == null ? -1 : tangentEntry.offsetBytes();
        int uvOff = uvEntry == null ? -1 : uvEntry.offsetBytes();
        int colorOff = colorEntry == null ? -1 : colorEntry.offsetBytes();
        int jointsOff = jointsEntry == null ? -1 : jointsEntry.offsetBytes();
        int weightsOff = weightsEntry == null ? -1 : weightsEntry.offsetBytes();

        boolean hasNormal = normalData != null && normalOff >= 0;
        boolean hasTangent = tangentData != null && tangentOff >= 0;
        boolean hasUv = uvData != null && uvOff >= 0;
        boolean hasColor = colorData != null && colorOff >= 0;
        boolean hasJoints = joints0 != null && jointsOff >= 0;
        boolean hasWeights = weightsData != null && weightsOff >= 0;
        VertexFormat normalFormat = normalEntry == null ? null : normalEntry.format();
        if (posEntry == null) {
            throw new IllegalStateException("PackSpec must include target format for POSITION[0]");
        }

        boolean hotPosNormalLayout = posOff == 0 && normalOff == 12 && stride == 16;
        boolean hotPosOnlyLayout = posOff == 0 && stride == 16;

        // Hot path for common unskinned meshes: position + normal only.
        if (!profileEnabled && hasNormal && !hasTangent && !hasUv && !hasColor && !hasJoints && !hasWeights) {
            int[] simdPackedNormals = null;
            boolean useSimdPackedNormals = SimdNormalPacker.isEnabled()
                && (normalFormat == VertexFormat.OCTA_SNORM16x2 || normalFormat == VertexFormat.SNORM8x4);
            if (useSimdPackedNormals) {
                simdPackedNormals = VertexWriteOps.ensureNormalPackScratch(vertexCount);
                if (normalFormat == VertexFormat.OCTA_SNORM16x2) {
                    SimdNormalPacker.packOctaNormals(normalData, vertexCount, simdPackedNormals);
                } else {
                    SimdNormalPacker.packSnorm8Normals(normalData, vertexCount, simdPackedNormals);
                }
            }
            if (hotPosNormalLayout) {
                for (int i = 0, p = 0; i < vertexCount; i++, p += 3) {
                    vertexBuffer.putFloat(positionData[p]);
                    vertexBuffer.putFloat(positionData[p + 1]);
                    vertexBuffer.putFloat(positionData[p + 2]);
                    int packedNormal = useSimdPackedNormals
                        ? simdPackedNormals[i]
                        : VertexWriteOps.packNormalToInt(normalFormat, normalData[p], normalData[p + 1], normalData[p + 2]);
                    vertexBuffer.putInt(packedNormal);
                }
            } else {
                for (int i = 0; i < vertexCount; i++) {
                    int base = i * stride;
                    int p = i * 3;
                    vertexBuffer.putFloat(base + posOff, positionData[p]);
                    vertexBuffer.putFloat(base + posOff + 4, positionData[p + 1]);
                    vertexBuffer.putFloat(base + posOff + 8, positionData[p + 2]);
                    if (useSimdPackedNormals) {
                        vertexBuffer.putInt(base + normalOff, simdPackedNormals[i]);
                    } else {
                        VertexWriteOps.writeNormal(vertexBuffer, base + normalOff, normalFormat, normalData[p], normalData[p + 1], normalData[p + 2]);
                    }
                }
            }
        } else if (!profileEnabled && !hasNormal && !hasTangent && !hasUv && !hasColor && !hasJoints && !hasWeights) {
            // Hot path for position-only meshes.
            if (hotPosOnlyLayout) {
                for (int p = 0; p < vertexCount * 3; p += 3) {
                    vertexBuffer.putFloat(positionData[p]);
                    vertexBuffer.putFloat(positionData[p + 1]);
                    vertexBuffer.putFloat(positionData[p + 2]);
                    vertexBuffer.putInt(0); // keep 16-byte stride deterministic
                }
            } else {
                for (int i = 0; i < vertexCount; i++) {
                    int base = i * stride;
                    int p = i * 3;
                    vertexBuffer.putFloat(base + posOff, positionData[p]);
                    vertexBuffer.putFloat(base + posOff + 4, positionData[p + 1]);
                    vertexBuffer.putFloat(base + posOff + 8, positionData[p + 2]);
                }
            }
        } else if (profileEnabled) {
            long posSamples = 0L;
            long normalSamples = 0L;
            long tangentSamples = 0L;
            long uvSamples = 0L;
            long colorSamples = 0L;
            long skinSamples = 0L;

            for (int i = 0; i < vertexCount; i++) {
                int base = i * stride;
                boolean sampleThisVertex = (i % PROFILE_SAMPLE_RATE) == 0;

                if (posOff >= 0) {
                    long sectionStart = sampleThisVertex ? System.nanoTime() : 0L;
                    int src = i * 3;
                    vertexBuffer.putFloat(base + posOff, positionData[src]);
                    vertexBuffer.putFloat(base + posOff + 4, positionData[src + 1]);
                    vertexBuffer.putFloat(base + posOff + 8, positionData[src + 2]);
                    if (sampleThisVertex) {
                        positionWriteNs += System.nanoTime() - sectionStart;
                        posSamples++;
                    }
                }

                if (hasNormal) {
                    long sectionStart = sampleThisVertex ? System.nanoTime() : 0L;
                    int src = i * 3;
                    VertexWriteOps.writeNormal(
                        vertexBuffer,
                        base + normalOff,
                        normalFormat,
                        normalData[src],
                        normalData[src + 1],
                        normalData[src + 2]
                    );
                    if (sampleThisVertex) {
                        normalPackNs += System.nanoTime() - sectionStart;
                        normalSamples++;
                    }
                }

                if (hasTangent) {
                    long sectionStart = sampleThisVertex ? System.nanoTime() : 0L;
                    int src = i * 4;
                    int packed = VertexWriteOps.packSnorm8x4Inline(
                        tangentData[src], tangentData[src + 1], tangentData[src + 2], tangentData[src + 3]);
                    vertexBuffer.putInt(base + tangentOff, packed);
                    if (sampleThisVertex) {
                        tangentPackNs += System.nanoTime() - sectionStart;
                        tangentSamples++;
                    }
                }

                if (hasUv) {
                    long sectionStart = sampleThisVertex ? System.nanoTime() : 0L;
                    int src = i * 2;
                    vertexBuffer.putShort(base + uvOff, org.dynamisengine.vectrix.gpu.Half.pack(uvData[src]));
                    vertexBuffer.putShort(base + uvOff + 2, org.dynamisengine.vectrix.gpu.Half.pack(uvData[src + 1]));
                    if (sampleThisVertex) {
                        uvPackNs += System.nanoTime() - sectionStart;
                        uvSamples++;
                    }
                }

                if (hasColor) {
                    long sectionStart = sampleThisVertex ? System.nanoTime() : 0L;
                    int src = i * 4;
                    int packed = org.dynamisengine.vectrix.gpu.PackedNorm.packUnorm8x4(
                        colorData[src], colorData[src + 1], colorData[src + 2], colorData[src + 3]);
                    vertexBuffer.putInt(base + colorOff, packed);
                    if (sampleThisVertex) {
                        colorPackNs += System.nanoTime() - sectionStart;
                        colorSamples++;
                    }
                }

                if (hasJoints) {
                    long sectionStart = sampleThisVertex ? System.nanoTime() : 0L;
                    int packed;
                    if (jointsData != null) {
                        int src = i * 4;
                        packed = (jointsData[src] & 0xFF)
                            | ((jointsData[src + 1] & 0xFF) << 8)
                            | ((jointsData[src + 2] & 0xFF) << 16)
                            | ((jointsData[src + 3] & 0xFF) << 24);
                    } else {
                        packed = (joints0.getInt(i, 0) & 0xFF)
                            | ((joints0.getInt(i, 1) & 0xFF) << 8)
                            | ((joints0.getInt(i, 2) & 0xFF) << 16)
                            | ((joints0.getInt(i, 3) & 0xFF) << 24);
                    }
                    vertexBuffer.putInt(base + jointsOff, packed);
                    if (sampleThisVertex) {
                        skinPackNs += System.nanoTime() - sectionStart;
                        skinSamples++;
                    }
                }

                if (hasWeights) {
                    long sectionStart = sampleThisVertex ? System.nanoTime() : 0L;
                    int src = i * 4;
                    int packed = org.dynamisengine.vectrix.gpu.PackedNorm.packUnorm8x4(
                        weightsData[src], weightsData[src + 1], weightsData[src + 2], weightsData[src + 3]);
                    vertexBuffer.putInt(base + weightsOff, packed);
                    if (sampleThisVertex) {
                        skinPackNs += System.nanoTime() - sectionStart;
                        skinSamples++;
                    }
                }
            }

            positionWriteNs = VertexWriteOps.scaleSampledNs(positionWriteNs, posSamples, vertexCount);
            normalPackNs = VertexWriteOps.scaleSampledNs(normalPackNs, normalSamples, vertexCount);
            tangentPackNs = VertexWriteOps.scaleSampledNs(tangentPackNs, tangentSamples, vertexCount);
            uvPackNs = VertexWriteOps.scaleSampledNs(uvPackNs, uvSamples, vertexCount);
            colorPackNs = VertexWriteOps.scaleSampledNs(colorPackNs, colorSamples, vertexCount);
            skinPackNs = VertexWriteOps.scaleSampledNs(skinPackNs, skinSamples, vertexCount);
        } else {
            VertexWriteOps.writeFusedPass(
                vertexBuffer,
                vertexCount,
                stride,
                positionData,
                posOff,
                normalData,
                hasNormal,
                normalOff,
                normalFormat,
                tangentData,
                hasTangent,
                tangentOff,
                uvData,
                hasUv,
                uvOff,
                colorData,
                hasColor,
                colorOff,
                jointsData,
                joints0,
                hasJoints,
                jointsOff,
                weightsData,
                hasWeights,
                weightsOff
            );
        }
        if (profile != null) {
            profile.setVertexWriteNs(System.nanoTime() - vertexWriteStart);
            profile.setPositionWriteNs(positionWriteNs);
            profile.setNormalPackNs(normalPackNs);
            profile.setTangentPackNs(tangentPackNs);
            profile.setUvPackNs(uvPackNs);
            profile.setColorPackNs(colorPackNs);
            profile.setSkinPackNs(skinPackNs);
        }

        long indexPackStart = profile == null ? 0L : System.nanoTime();
        PackedMesh.IndexBufferView indexBuffer = null;
        int[] indices = mesh.indicesOrNull();
        if (indices != null) {
            indexBuffer = IndexPacker.packIndices(indices, spec.indexPolicy());
        }
        if (profile != null) {
            profile.setIndexPackNs(System.nanoTime() - indexPackStart);
            profile.setIndexCount(indices == null ? 0 : indices.length);
        }

        long submeshCopyStart = profile == null ? 0L : System.nanoTime();
        List<PackedMesh.SubmeshRange> submeshes = copySubmeshRanges(mesh.submeshes());
        if (profile != null) {
            profile.setSubmeshCopyNs(System.nanoTime() - submeshCopyStart);
            profile.setTotalNs(System.nanoTime() - totalStart);
        }

        MeshletBufferView meshlets = null;
        ByteBuffer meshletDescriptorBuffer = null;
        int meshletDescriptorStrideBytes = 0;
        if (spec.meshletsEnabled() && indices != null && indices.length > 0) {
            meshlets = MeshletBufferView.of(
                MeshletClusters.buildMeshlets(mesh, indices, spec.maxMeshletVertices(), spec.maxMeshletTriangles())
            );
            meshletDescriptorStrideBytes = MeshletBuffers.descriptorStrideBytes(spec.alignmentBytes());
            meshletDescriptorBuffer = MeshletBuffers.packDescriptors(meshlets, spec.alignmentBytes());
        }

        return new PackedMesh(
            layout,
            vertexBuffer,
            indexBuffer,
            submeshes,
            meshlets,
            meshletDescriptorBuffer,
            meshletDescriptorStrideBytes
        );
    }

    // -- Shared utilities used by RuntimeMeshPacker and RuntimePackWorkspace --

    static int add(
        LinkedHashMap<AttributeKey, VertexLayout.Entry> entries,
        int offset,
        AttributeKey key,
        VertexFormat format
    ) {
        if (format == null) {
            return offset;
        }
        entries.put(key, new VertexLayout.Entry(key, format, offset));
        return offset + format.bytesPerVertex();
    }

    static VertexLayout buildLayout(
        PackSpec spec,
        int layoutMask,
        VertexFormat positionFormat,
        VertexFormat normalFormat,
        VertexFormat tangentFormat,
        VertexFormat uvFormat,
        VertexFormat colorFormat,
        VertexFormat jointsFormat,
        VertexFormat weightsFormat
    ) {
        int offset = 0;
        LinkedHashMap<AttributeKey, VertexLayout.Entry> entries = new LinkedHashMap<>();
        offset = add(entries, offset, POSITION_0, positionFormat);
        if ((layoutMask & MASK_NORMAL) != 0) {
            offset = add(entries, offset, NORMAL_0, normalFormat);
        }
        if ((layoutMask & MASK_TANGENT) != 0) {
            offset = add(entries, offset, TANGENT_0, tangentFormat);
        }
        if ((layoutMask & MASK_UV) != 0) {
            offset = add(entries, offset, UV_0, uvFormat);
        }
        if ((layoutMask & MASK_COLOR) != 0) {
            offset = add(entries, offset, COLOR_0, colorFormat);
        }
        if ((layoutMask & MASK_JOINTS) != 0) {
            offset = add(entries, offset, JOINTS_0, jointsFormat);
        }
        if ((layoutMask & MASK_WEIGHTS) != 0) {
            offset = add(entries, offset, WEIGHTS_0, weightsFormat);
        }
        int stride = align(offset, spec.alignmentBytes());
        return new VertexLayout(stride, entries);
    }

    static int align(int value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    static VertexAttributeView require(MeshData mesh, AttributeKey key) {
        VertexAttributeView view = mesh.attributeOrNull(key);
        if (view == null) {
            throw new IllegalStateException("Missing required attribute: " + key.semantic() + "[" + key.setIndex() + "]");
        }
        return view;
    }

    static VertexAttributeView optional(MeshData mesh, AttributeKey key) {
        return mesh.attributeOrNull(key);
    }

    static float[] requireFloat(VertexAttributeView view, String label) {
        float[] values = view.rawFloatArrayOrNull();
        if (values == null) {
            throw new IllegalStateException(label + " must be float-backed in authoring MeshData for v1 packer");
        }
        return values;
    }

    private static List<PackedMesh.SubmeshRange> copySubmeshRanges(List<Submesh> source) {
        int size = source.size();
        if (size == 0) {
            return List.of();
        }
        if (size == 1) {
            Submesh submesh = source.getFirst();
            return List.of(new PackedMesh.SubmeshRange(
                submesh.firstIndex(),
                submesh.indexCount(),
                submesh.materialId()
            ));
        }
        ArrayList<PackedMesh.SubmeshRange> ranges = new ArrayList<>(size);
        for (Submesh submesh : source) {
            ranges.add(new PackedMesh.SubmeshRange(
                submesh.firstIndex(),
                submesh.indexCount(),
                submesh.materialId()
            ));
        }
        return ranges;
    }
}
