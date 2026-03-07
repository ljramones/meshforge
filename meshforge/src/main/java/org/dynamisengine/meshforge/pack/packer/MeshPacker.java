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
import org.dynamisengine.vectrix.gpu.Half;
import org.dynamisengine.vectrix.gpu.OctaNormal;
import org.dynamisengine.vectrix.gpu.PackedNorm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

/**
 * Public class MeshPacker.
 */
public final class MeshPacker {
    private static final int PROFILE_SAMPLE_RATE = 1024;
    private static final Logger LOGGER = Logger.getLogger(MeshPacker.class.getName());
    private static final AttributeKey POSITION_0 = new AttributeKey(AttributeSemantic.POSITION, 0);
    private static final AttributeKey NORMAL_0 = new AttributeKey(AttributeSemantic.NORMAL, 0);
    private static final AttributeKey TANGENT_0 = new AttributeKey(AttributeSemantic.TANGENT, 0);
    private static final AttributeKey UV_0 = new AttributeKey(AttributeSemantic.UV, 0);
    private static final AttributeKey COLOR_0 = new AttributeKey(AttributeSemantic.COLOR, 0);
    private static final AttributeKey JOINTS_0 = new AttributeKey(AttributeSemantic.JOINTS, 0);
    private static final AttributeKey WEIGHTS_0 = new AttributeKey(AttributeSemantic.WEIGHTS, 0);
    private static final ThreadLocal<int[]> NORMAL_PACK_SCRATCH = ThreadLocal.withInitial(() -> new int[0]);

    private MeshPacker() {
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
        VertexAttributeView position = require(mesh, AttributeSemantic.POSITION, 0);
        VertexAttributeView normal = optional(mesh, AttributeSemantic.NORMAL, 0);
        VertexAttributeView tangent = optional(mesh, AttributeSemantic.TANGENT, 0);
        VertexAttributeView uv0 = optional(mesh, AttributeSemantic.UV, 0);
        VertexAttributeView color0 = optional(mesh, AttributeSemantic.COLOR, 0);
        VertexAttributeView joints0 = optional(mesh, AttributeSemantic.JOINTS, 0);
        VertexAttributeView weights0 = optional(mesh, AttributeSemantic.WEIGHTS, 0);

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
                simdPackedNormals = ensureNormalPackScratch(vertexCount);
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
                        : packNormalToInt(normalFormat, normalData[p], normalData[p + 1], normalData[p + 2]);
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
                        writeNormal(vertexBuffer, base + normalOff, normalFormat, normalData[p], normalData[p + 1], normalData[p + 2]);
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
                boolean sampleThisVertex = profileEnabled && ((i % PROFILE_SAMPLE_RATE) == 0);

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
                    writeNormal(
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
                    int packed = packSnorm8x4Inline(
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
                    vertexBuffer.putShort(base + uvOff, Half.pack(uvData[src]));
                    vertexBuffer.putShort(base + uvOff + 2, Half.pack(uvData[src + 1]));
                    if (sampleThisVertex) {
                        uvPackNs += System.nanoTime() - sectionStart;
                        uvSamples++;
                    }
                }

                if (hasColor) {
                    long sectionStart = sampleThisVertex ? System.nanoTime() : 0L;
                    int src = i * 4;
                    int packed = PackedNorm.packUnorm8x4(
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
                    int packed = PackedNorm.packUnorm8x4(
                        weightsData[src], weightsData[src + 1], weightsData[src + 2], weightsData[src + 3]);
                    vertexBuffer.putInt(base + weightsOff, packed);
                    if (sampleThisVertex) {
                        skinPackNs += System.nanoTime() - sectionStart;
                        skinSamples++;
                    }
                }
            }

            if (profileEnabled) {
                positionWriteNs = scaleSampledNs(positionWriteNs, posSamples, vertexCount);
                normalPackNs = scaleSampledNs(normalPackNs, normalSamples, vertexCount);
                tangentPackNs = scaleSampledNs(tangentPackNs, tangentSamples, vertexCount);
                uvPackNs = scaleSampledNs(uvPackNs, uvSamples, vertexCount);
                colorPackNs = scaleSampledNs(colorPackNs, colorSamples, vertexCount);
                skinPackNs = scaleSampledNs(skinPackNs, skinSamples, vertexCount);
            }
        } else {
            writeFusedPass(
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
            indexBuffer = packIndices(indices, spec.indexPolicy());
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

    private static int add(
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

    private static void writePositionPass(ByteBuffer out, float[] positionData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 3) {
            int base = (i * stride) + offset;
            out.putFloat(base, positionData[src]);
            out.putFloat(base + 4, positionData[src + 1]);
            out.putFloat(base + 8, positionData[src + 2]);
        }
    }

    private static void writeNormalPass(
        ByteBuffer out,
        float[] normalData,
        int vertexCount,
        int stride,
        int offset,
        VertexFormat normalFormat
    ) {
        if (SimdNormalPacker.isEnabled()
            && (normalFormat == VertexFormat.OCTA_SNORM16x2 || normalFormat == VertexFormat.SNORM8x4)) {
            int[] packed = ensureNormalPackScratch(vertexCount);
            if (normalFormat == VertexFormat.OCTA_SNORM16x2) {
                SimdNormalPacker.packOctaNormals(normalData, vertexCount, packed);
            } else {
                SimdNormalPacker.packSnorm8Normals(normalData, vertexCount, packed);
            }
            for (int i = 0; i < vertexCount; i++) {
                out.putInt((i * stride) + offset, packed[i]);
            }
            return;
        }

        for (int i = 0, src = 0; i < vertexCount; i++, src += 3) {
            writeNormal(out, (i * stride) + offset, normalFormat, normalData[src], normalData[src + 1], normalData[src + 2]);
        }
    }

    private static void writeTangentPass(ByteBuffer out, float[] tangentData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 4) {
            int packed = packSnorm8x4Inline(
                tangentData[src], tangentData[src + 1], tangentData[src + 2], tangentData[src + 3]);
            out.putInt((i * stride) + offset, packed);
        }
    }

    private static void writeUvPass(ByteBuffer out, float[] uvData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 2) {
            int base = (i * stride) + offset;
            out.putShort(base, Half.pack(uvData[src]));
            out.putShort(base + 2, Half.pack(uvData[src + 1]));
        }
    }

    private static void writeColorPass(ByteBuffer out, float[] colorData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 4) {
            int packed = PackedNorm.packUnorm8x4(
                colorData[src], colorData[src + 1], colorData[src + 2], colorData[src + 3]);
            out.putInt((i * stride) + offset, packed);
        }
    }

    private static void writeJointsPass(
        ByteBuffer out,
        int[] jointsData,
        VertexAttributeView jointsView,
        int vertexCount,
        int stride,
        int offset
    ) {
        if (jointsData != null) {
            for (int i = 0, src = 0; i < vertexCount; i++, src += 4) {
                int packed = (jointsData[src] & 0xFF)
                    | ((jointsData[src + 1] & 0xFF) << 8)
                    | ((jointsData[src + 2] & 0xFF) << 16)
                    | ((jointsData[src + 3] & 0xFF) << 24);
                out.putInt((i * stride) + offset, packed);
            }
            return;
        }

        for (int i = 0; i < vertexCount; i++) {
            int packed = (jointsView.getInt(i, 0) & 0xFF)
                | ((jointsView.getInt(i, 1) & 0xFF) << 8)
                | ((jointsView.getInt(i, 2) & 0xFF) << 16)
                | ((jointsView.getInt(i, 3) & 0xFF) << 24);
            out.putInt((i * stride) + offset, packed);
        }
    }

    private static void writeWeightsPass(ByteBuffer out, float[] weightsData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 4) {
            int packed = PackedNorm.packUnorm8x4(
                weightsData[src], weightsData[src + 1], weightsData[src + 2], weightsData[src + 3]);
            out.putInt((i * stride) + offset, packed);
        }
    }

    private static void writeFusedPass(
        ByteBuffer out,
        int vertexCount,
        int stride,
        float[] positionData,
        int posOff,
        float[] normalData,
        boolean hasNormal,
        int normalOff,
        VertexFormat normalFormat,
        float[] tangentData,
        boolean hasTangent,
        int tangentOff,
        float[] uvData,
        boolean hasUv,
        int uvOff,
        float[] colorData,
        boolean hasColor,
        int colorOff,
        int[] jointsData,
        VertexAttributeView jointsView,
        boolean hasJoints,
        int jointsOff,
        float[] weightsData,
        boolean hasWeights,
        int weightsOff
    ) {
        for (int i = 0; i < vertexCount; i++) {
            int base = i * stride;
            if (posOff >= 0) {
                int src = i * 3;
                out.putFloat(base + posOff, positionData[src]);
                out.putFloat(base + posOff + 4, positionData[src + 1]);
                out.putFloat(base + posOff + 8, positionData[src + 2]);
            }
            if (hasNormal) {
                int src = i * 3;
                writeNormal(out, base + normalOff, normalFormat, normalData[src], normalData[src + 1], normalData[src + 2]);
            }
            if (hasTangent) {
                int src = i * 4;
                int packed = packSnorm8x4Inline(
                    tangentData[src], tangentData[src + 1], tangentData[src + 2], tangentData[src + 3]
                );
                out.putInt(base + tangentOff, packed);
            }
            if (hasUv) {
                int src = i * 2;
                out.putShort(base + uvOff, Half.pack(uvData[src]));
                out.putShort(base + uvOff + 2, Half.pack(uvData[src + 1]));
            }
            if (hasColor) {
                int src = i * 4;
                int packed = PackedNorm.packUnorm8x4(
                    colorData[src], colorData[src + 1], colorData[src + 2], colorData[src + 3]
                );
                out.putInt(base + colorOff, packed);
            }
            if (hasJoints) {
                int packed;
                if (jointsData != null) {
                    int src = i * 4;
                    packed = (jointsData[src] & 0xFF)
                        | ((jointsData[src + 1] & 0xFF) << 8)
                        | ((jointsData[src + 2] & 0xFF) << 16)
                        | ((jointsData[src + 3] & 0xFF) << 24);
                } else {
                    packed = (jointsView.getInt(i, 0) & 0xFF)
                        | ((jointsView.getInt(i, 1) & 0xFF) << 8)
                        | ((jointsView.getInt(i, 2) & 0xFF) << 16)
                        | ((jointsView.getInt(i, 3) & 0xFF) << 24);
                }
                out.putInt(base + jointsOff, packed);
            }
            if (hasWeights) {
                int src = i * 4;
                int packed = PackedNorm.packUnorm8x4(
                    weightsData[src], weightsData[src + 1], weightsData[src + 2], weightsData[src + 3]
                );
                out.putInt(base + weightsOff, packed);
            }
        }
    }

    private static int align(int value, int alignment) {
        if (alignment <= 1) {
            return value;
        }
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    private static VertexAttributeView require(MeshData mesh, AttributeSemantic semantic, int setIndex) {
        if (!mesh.has(semantic, setIndex)) {
            throw new IllegalStateException("Missing required attribute: " + semantic + "[" + setIndex + "]");
        }
        return mesh.attribute(semantic, setIndex);
    }

    private static VertexAttributeView optional(MeshData mesh, AttributeSemantic semantic, int setIndex) {
        return mesh.has(semantic, setIndex) ? mesh.attribute(semantic, setIndex) : null;
    }

    private static float[] requireFloat(VertexAttributeView view, String label) {
        float[] values = view.rawFloatArrayOrNull();
        if (values == null) {
            throw new IllegalStateException(label + " must be float-backed in authoring MeshData for v1 packer");
        }
        return values;
    }

    private static PackedMesh.IndexBufferView packIndices(int[] indices, PackSpec.IndexPolicy policy) {
        if (indices.length == Integer.MAX_VALUE) {
            throw new IllegalStateException("indexCount exceeds supported limit: " + indices.length);
        }
        boolean canUse16 = true;
        for (int value : indices) {
            if (value < 0) {
                throw new IllegalStateException("Index buffer contains negative index: " + value);
            }
            if ((value & 0xFFFF0000) != 0) {
                canUse16 = false;
                break;
            }
        }
        if (policy == PackSpec.IndexPolicy.FORCE_32) {
            canUse16 = false;
        }

        if (canUse16) {
            final ByteBuffer data;
            try {
                data = ByteBuffer.allocateDirect(Math.multiplyExact(indices.length, 2)).order(ByteOrder.LITTLE_ENDIAN);
            } catch (ArithmeticException ex) {
                throw new IllegalStateException("Index buffer size overflow for UINT16 with indexCount=" + indices.length, ex);
            }
            for (int value : indices) {
                data.putShort((short) value);
            }
            data.flip();
            return new PackedMesh.IndexBufferView(PackedMesh.IndexType.UINT16, data, indices.length);
        }

        final ByteBuffer data;
        try {
            data = ByteBuffer.allocateDirect(Math.multiplyExact(indices.length, 4)).order(ByteOrder.LITTLE_ENDIAN);
        } catch (ArithmeticException ex) {
            throw new IllegalStateException("Index buffer size overflow for UINT32 with indexCount=" + indices.length, ex);
        }
        for (int value : indices) {
            data.putInt(value);
        }
        data.flip();
        return new PackedMesh.IndexBufferView(PackedMesh.IndexType.UINT32, data, indices.length);
    }

    private static int packSnorm8x4Inline(float x, float y, float z, float w) {
        int xi = snorm8(x);
        int yi = snorm8(y);
        int zi = snorm8(z);
        int wi = snorm8(w);
        return (xi & 0xFF)
            | ((yi & 0xFF) << 8)
            | ((zi & 0xFF) << 16)
            | ((wi & 0xFF) << 24);
    }

    private static int packNormalToInt(VertexFormat format, float x, float y, float z) {
        if (format == VertexFormat.OCTA_SNORM16x2) {
            return OctaNormal.encodeSnorm16(x, y, z);
        }
        if (format == VertexFormat.SNORM8x4) {
            return packSnorm8x4Inline(x, y, z, 0.0f);
        }
        throw new IllegalStateException("Unsupported packed normal format: " + format);
    }

    private static void writeNormal(ByteBuffer out, int offset, VertexFormat format, float x, float y, float z) {
        if (format == VertexFormat.F32x3) {
            out.putFloat(offset, x);
            out.putFloat(offset + 4, y);
            out.putFloat(offset + 8, z);
            return;
        }
        out.putInt(offset, packNormalToInt(format, x, y, z));
    }

    private static int snorm8(float v) {
        float c = Math.max(-1.0f, Math.min(1.0f, v));
        // SNORM8 range [-127, 127].
        int q = Math.round(c * 127.0f);
        return q & 0xFF;
    }

    private static long scaleSampledNs(long sampledNs, long samples, int totalVertices) {
        if (sampledNs <= 0L || samples <= 0L || totalVertices <= 0) {
            return 0L;
        }
        double scale = (double) totalVertices / (double) samples;
        return (long) (sampledNs * scale);
    }

    private static int[] ensureNormalPackScratch(int minLength) {
        int[] scratch = NORMAL_PACK_SCRATCH.get();
        if (scratch.length >= minLength) {
            return scratch;
        }
        int[] grown = new int[minLength];
        NORMAL_PACK_SCRATCH.set(grown);
        return grown;
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
