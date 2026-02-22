package org.meshforge.pack.simd;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
import org.vectrix.gpu.OctaNormal;
import org.vectrix.gpu.PackedNorm;

/**
 * SIMD-assisted normal packing kernels.
 */
public final class SimdNormalPacker {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;
    private static final boolean ENABLED = Boolean.parseBoolean(
        System.getProperty("meshforge.pack.simd.enabled", "true"));

    private SimdNormalPacker() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    /**
     * Packs XYZ normals into packed octahedral SNORM16x2 integers.
     * <p>
     * This keeps the final quantization path aligned with Vectrix by using
     * {@link OctaNormal#encodeSnorm16(float, float, float)} per lane after SIMD normalization.
     */
    public static void packOctaNormals(float[] xyz, int vertexCount, int[] outPacked) {
        if (xyz == null) {
            throw new NullPointerException("xyz");
        }
        if (outPacked == null) {
            throw new NullPointerException("outPacked");
        }
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount must be >= 0");
        }
        if (xyz.length < vertexCount * 3) {
            throw new IllegalArgumentException("xyz array too small for vertexCount");
        }
        if (outPacked.length < vertexCount) {
            throw new IllegalArgumentException("outPacked array too small for vertexCount");
        }

        int lanes = SPECIES.length();
        float[] xs = new float[lanes];
        float[] ys = new float[lanes];
        float[] zs = new float[lanes];

        int i = 0;
        int upper = vertexCount - (vertexCount % lanes);
        for (; i < upper; i += lanes) {
            for (int lane = 0; lane < lanes; lane++) {
                int src = (i + lane) * 3;
                xs[lane] = xyz[src];
                ys[lane] = xyz[src + 1];
                zs[lane] = xyz[src + 2];
            }

            FloatVector vx = FloatVector.fromArray(SPECIES, xs, 0);
            FloatVector vy = FloatVector.fromArray(SPECIES, ys, 0);
            FloatVector vz = FloatVector.fromArray(SPECIES, zs, 0);

            FloatVector lenSq = vx.mul(vx).add(vy.mul(vy)).add(vz.mul(vz));
            FloatVector len = lenSq.sqrt();
            VectorMask<Float> nonZero = len.compare(jdk.incubator.vector.VectorOperators.GT, 0.0f);

            // Normalize only where length is non-zero; keep zeros stable.
            vx = vx.div(len).blend(vx, nonZero.not());
            vy = vy.div(len).blend(vy, nonZero.not());
            vz = vz.div(len).blend(vz, nonZero.not());

            vx.intoArray(xs, 0);
            vy.intoArray(ys, 0);
            vz.intoArray(zs, 0);

            for (int lane = 0; lane < lanes; lane++) {
                outPacked[i + lane] = OctaNormal.encodeSnorm16(xs[lane], ys[lane], zs[lane]);
            }
        }

        for (; i < vertexCount; i++) {
            int src = i * 3;
            outPacked[i] = OctaNormal.encodeSnorm16(xyz[src], xyz[src + 1], xyz[src + 2]);
        }
    }

    /**
     * Packs XYZ normals into SNORM8x4 (w = 0) packed integers.
     * <p>
     * Preserves scalar packing behavior for compatibility (no pre-normalization).
     */
    public static void packSnorm8Normals(float[] xyz, int vertexCount, int[] outPacked) {
        if (xyz == null) {
            throw new NullPointerException("xyz");
        }
        if (outPacked == null) {
            throw new NullPointerException("outPacked");
        }
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount must be >= 0");
        }
        if (xyz.length < vertexCount * 3) {
            throw new IllegalArgumentException("xyz array too small for vertexCount");
        }
        if (outPacked.length < vertexCount) {
            throw new IllegalArgumentException("outPacked array too small for vertexCount");
        }

        int lanes = SPECIES.length();
        float[] xs = new float[lanes];
        float[] ys = new float[lanes];
        float[] zs = new float[lanes];

        int i = 0;
        int upper = vertexCount - (vertexCount % lanes);
        for (; i < upper; i += lanes) {
            for (int lane = 0; lane < lanes; lane++) {
                int src = (i + lane) * 3;
                xs[lane] = xyz[src];
                ys[lane] = xyz[src + 1];
                zs[lane] = xyz[src + 2];
            }

            FloatVector vx = FloatVector.fromArray(SPECIES, xs, 0);
            FloatVector vy = FloatVector.fromArray(SPECIES, ys, 0);
            FloatVector vz = FloatVector.fromArray(SPECIES, zs, 0);

            // Clamp in SIMD, then keep scalar quantization behavior via PackedNorm.
            vx = vx.max(-1.0f).min(1.0f);
            vy = vy.max(-1.0f).min(1.0f);
            vz = vz.max(-1.0f).min(1.0f);
            vx.intoArray(xs, 0);
            vy.intoArray(ys, 0);
            vz.intoArray(zs, 0);

            for (int lane = 0; lane < lanes; lane++) {
                outPacked[i + lane] = PackedNorm.packSnorm8x4(xs[lane], ys[lane], zs[lane], 0.0f);
            }
        }

        for (; i < vertexCount; i++) {
            int src = i * 3;
            outPacked[i] = PackedNorm.packSnorm8x4(xyz[src], xyz[src + 1], xyz[src + 2], 0.0f);
        }
    }
}
