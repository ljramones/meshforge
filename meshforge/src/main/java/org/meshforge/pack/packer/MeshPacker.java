package org.meshforge.pack.packer;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.AttributeSemantic;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;
import org.meshforge.core.mesh.MeshData;
import org.meshforge.core.mesh.Submesh;
import org.meshforge.pack.buffer.PackedMesh;
import org.meshforge.pack.layout.VertexLayout;
import org.meshforge.pack.spec.PackSpec;
import org.vectrix.gpu.Half;
import org.vectrix.gpu.PackedNorm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public final class MeshPacker {
    private static final int PROFILE_SAMPLE_RATE = 1024;

    private MeshPacker() {
    }

    public static PackedMesh pack(MeshData mesh, PackSpec spec) {
        return pack(mesh, spec, null);
    }

    public static PackedMesh pack(MeshData mesh, PackSpec spec, PackProfile profile) {
        if (profile != null) {
            profile.reset();
        }
        long totalStart = profile == null ? 0L : System.nanoTime();
        if (spec.layoutMode() != PackSpec.LayoutMode.INTERLEAVED) {
            throw new UnsupportedOperationException("v1 supports INTERLEAVED only");
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

        offset = add(entries, offset, new AttributeKey(AttributeSemantic.POSITION, 0),
            spec.targetFormat(AttributeSemantic.POSITION, 0));
        if (normal != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.NORMAL, 0),
                spec.targetFormat(AttributeSemantic.NORMAL, 0));
        }
        if (tangent != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.TANGENT, 0),
                spec.targetFormat(AttributeSemantic.TANGENT, 0));
        }
        if (uv0 != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.UV, 0),
                spec.targetFormat(AttributeSemantic.UV, 0));
        }
        if (color0 != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.COLOR, 0),
                spec.targetFormat(AttributeSemantic.COLOR, 0));
        }
        if (joints0 != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.JOINTS, 0),
                spec.targetFormat(AttributeSemantic.JOINTS, 0));
        }
        if (weights0 != null) {
            offset = add(entries, offset, new AttributeKey(AttributeSemantic.WEIGHTS, 0),
                spec.targetFormat(AttributeSemantic.WEIGHTS, 0));
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
        ByteBuffer vertexBuffer = ByteBuffer.allocateDirect(vertexCount * stride).order(ByteOrder.LITTLE_ENDIAN);

        float[] positionData = requireFloat(position, "POSITION");
        float[] normalData = normal == null ? null : normal.rawFloatArrayOrNull();
        float[] tangentData = tangent == null ? null : tangent.rawFloatArrayOrNull();
        float[] uvData = uv0 == null ? null : uv0.rawFloatArrayOrNull();
        float[] colorData = color0 == null ? null : color0.rawFloatArrayOrNull();

        int[] jointsData = joints0 == null ? null : joints0.rawIntArrayOrNull();
        float[] weightsData = weights0 == null ? null : weights0.rawFloatArrayOrNull();

        VertexLayout.Entry posEntry = layout.entry(new AttributeKey(AttributeSemantic.POSITION, 0));
        VertexLayout.Entry normalEntry = layout.entry(new AttributeKey(AttributeSemantic.NORMAL, 0));
        VertexLayout.Entry tangentEntry = layout.entry(new AttributeKey(AttributeSemantic.TANGENT, 0));
        VertexLayout.Entry uvEntry = layout.entry(new AttributeKey(AttributeSemantic.UV, 0));
        VertexLayout.Entry colorEntry = layout.entry(new AttributeKey(AttributeSemantic.COLOR, 0));
        VertexLayout.Entry jointsEntry = layout.entry(new AttributeKey(AttributeSemantic.JOINTS, 0));
        VertexLayout.Entry weightsEntry = layout.entry(new AttributeKey(AttributeSemantic.WEIGHTS, 0));

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
        boolean hasJoints = jointsData != null && jointsOff >= 0;
        boolean hasWeights = weightsData != null && weightsOff >= 0;

        boolean hotPosNormalLayout = posOff == 0 && normalOff == 12 && stride == 16;
        boolean hotPosOnlyLayout = posOff == 0 && stride == 16;

        // Hot path for common unskinned meshes: position + normal only.
        if (!profileEnabled && hasNormal && !hasTangent && !hasUv && !hasColor && !hasJoints && !hasWeights) {
            if (hotPosNormalLayout) {
                for (int p = 0; p < vertexCount * 3; p += 3) {
                    vertexBuffer.putFloat(positionData[p]);
                    vertexBuffer.putFloat(positionData[p + 1]);
                    vertexBuffer.putFloat(positionData[p + 2]);
                    vertexBuffer.putInt(packSnorm8x4Inline(normalData[p], normalData[p + 1], normalData[p + 2], 0.0f));
                }
            } else {
                for (int i = 0; i < vertexCount; i++) {
                    int base = i * stride;
                    int p = i * 3;
                    vertexBuffer.putFloat(base + posOff, positionData[p]);
                    vertexBuffer.putFloat(base + posOff + 4, positionData[p + 1]);
                    vertexBuffer.putFloat(base + posOff + 8, positionData[p + 2]);
                    vertexBuffer.putInt(base + normalOff, packSnorm8x4Inline(normalData[p], normalData[p + 1], normalData[p + 2], 0.0f));
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
        } else {
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
                    int packed = packSnorm8x4Inline(normalData[src], normalData[src + 1], normalData[src + 2], 0.0f);
                    vertexBuffer.putInt(base + normalOff, packed);
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
                    int src = i * 4;
                    int packed = (jointsData[src] & 0xFF)
                        | ((jointsData[src + 1] & 0xFF) << 8)
                        | ((jointsData[src + 2] & 0xFF) << 16)
                        | ((jointsData[src + 3] & 0xFF) << 24);
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
        List<PackedMesh.SubmeshRange> submeshes = new ArrayList<>();
        for (Submesh submesh : mesh.submeshes()) {
            submeshes.add(new PackedMesh.SubmeshRange(submesh.firstIndex(), submesh.indexCount(), submesh.materialId()));
        }
        if (profile != null) {
            profile.setSubmeshCopyNs(System.nanoTime() - submeshCopyStart);
            profile.setTotalNs(System.nanoTime() - totalStart);
        }

        return new PackedMesh(layout, vertexBuffer, indexBuffer, submeshes);
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
        boolean canUse16 = true;
        for (int value : indices) {
            if ((value & 0xFFFF0000) != 0) {
                canUse16 = false;
                break;
            }
        }
        if (policy == PackSpec.IndexPolicy.FORCE_32) {
            canUse16 = false;
        }

        if (canUse16) {
            ByteBuffer data = ByteBuffer.allocateDirect(indices.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            for (int value : indices) {
                data.putShort((short) value);
            }
            data.flip();
            return new PackedMesh.IndexBufferView(PackedMesh.IndexType.UINT16, data, indices.length);
        }

        ByteBuffer data = ByteBuffer.allocateDirect(indices.length * 4).order(ByteOrder.LITTLE_ENDIAN);
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
}
