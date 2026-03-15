package org.dynamisengine.meshforge.pack.packer;

import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;
import org.dynamisengine.meshforge.pack.simd.SimdNormalPacker;
import org.dynamisengine.vectrix.gpu.Half;
import org.dynamisengine.vectrix.gpu.OctaNormal;
import org.dynamisengine.vectrix.gpu.PackedNorm;

import java.nio.ByteBuffer;

/**
 * Low-level vertex attribute write passes extracted from {@link MeshPacker}.
 * All methods are static, stateless, and designed for hot-path usage.
 */
final class VertexWriteOps {

    private static final ThreadLocal<int[]> NORMAL_PACK_SCRATCH = ThreadLocal.withInitial(() -> new int[0]);

    private VertexWriteOps() {
    }

    static void writePositionPass(ByteBuffer out, float[] positionData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 3) {
            int base = (i * stride) + offset;
            out.putFloat(base, positionData[src]);
            out.putFloat(base + 4, positionData[src + 1]);
            out.putFloat(base + 8, positionData[src + 2]);
        }
    }

    static void writeNormalPass(
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

    static void writeTangentPass(ByteBuffer out, float[] tangentData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 4) {
            int packed = packSnorm8x4Inline(
                tangentData[src], tangentData[src + 1], tangentData[src + 2], tangentData[src + 3]);
            out.putInt((i * stride) + offset, packed);
        }
    }

    static void writeUvPass(ByteBuffer out, float[] uvData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 2) {
            int base = (i * stride) + offset;
            out.putShort(base, Half.pack(uvData[src]));
            out.putShort(base + 2, Half.pack(uvData[src + 1]));
        }
    }

    static void writeColorPass(ByteBuffer out, float[] colorData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 4) {
            int packed = PackedNorm.packUnorm8x4(
                colorData[src], colorData[src + 1], colorData[src + 2], colorData[src + 3]);
            out.putInt((i * stride) + offset, packed);
        }
    }

    static void writeJointsPass(
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

    static void writeWeightsPass(ByteBuffer out, float[] weightsData, int vertexCount, int stride, int offset) {
        for (int i = 0, src = 0; i < vertexCount; i++, src += 4) {
            int packed = PackedNorm.packUnorm8x4(
                weightsData[src], weightsData[src + 1], weightsData[src + 2], weightsData[src + 3]);
            out.putInt((i * stride) + offset, packed);
        }
    }

    static void writeFusedPass(
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

    static int packSnorm8x4Inline(float x, float y, float z, float w) {
        int xi = snorm8(x);
        int yi = snorm8(y);
        int zi = snorm8(z);
        int wi = snorm8(w);
        return (xi & 0xFF)
            | ((yi & 0xFF) << 8)
            | ((zi & 0xFF) << 16)
            | ((wi & 0xFF) << 24);
    }

    static int packNormalToInt(VertexFormat format, float x, float y, float z) {
        if (format == VertexFormat.OCTA_SNORM16x2) {
            return OctaNormal.encodeSnorm16(x, y, z);
        }
        if (format == VertexFormat.SNORM8x4) {
            return packSnorm8x4Inline(x, y, z, 0.0f);
        }
        throw new IllegalStateException("Unsupported packed normal format: " + format);
    }

    static void writeNormal(ByteBuffer out, int offset, VertexFormat format, float x, float y, float z) {
        if (format == VertexFormat.F32x3) {
            out.putFloat(offset, x);
            out.putFloat(offset + 4, y);
            out.putFloat(offset + 8, z);
            return;
        }
        out.putInt(offset, packNormalToInt(format, x, y, z));
    }

    static int snorm8(float v) {
        float c = Math.max(-1.0f, Math.min(1.0f, v));
        int q = Math.round(c * 127.0f);
        return q & 0xFF;
    }

    static long scaleSampledNs(long sampledNs, long samples, int totalVertices) {
        if (sampledNs <= 0L || samples <= 0L || totalVertices <= 0) {
            return 0L;
        }
        double scale = (double) totalVertices / (double) samples;
        return (long) (sampledNs * scale);
    }

    static int[] ensureNormalPackScratch(int minLength) {
        int[] scratch = NORMAL_PACK_SCRATCH.get();
        if (scratch.length >= minLength) {
            return scratch;
        }
        int[] grown = new int[minLength];
        NORMAL_PACK_SCRATCH.set(grown);
        return grown;
    }
}
