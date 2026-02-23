package org.meshforge.core.mesh;

/**
 * Per-vertex morph target deltas for base-mesh deformation.
 * POSITION deltas are required; NORMAL/TANGENT deltas are optional.
 */
public final class MorphTarget {
    private final String name;
    private final float[] positionDeltas;
    private final float[] normalDeltas;
    private final float[] tangentDeltas;

    public MorphTarget(String name, float[] positionDeltas, float[] normalDeltas, float[] tangentDeltas) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (positionDeltas == null) {
            throw new NullPointerException("positionDeltas");
        }
        this.name = name;
        this.positionDeltas = positionDeltas.clone();
        this.normalDeltas = normalDeltas == null ? null : normalDeltas.clone();
        this.tangentDeltas = tangentDeltas == null ? null : tangentDeltas.clone();
    }

    public String name() {
        return name;
    }

    public float[] positionDeltas() {
        return positionDeltas.clone();
    }

    public float[] normalDeltas() {
        return normalDeltas == null ? null : normalDeltas.clone();
    }

    public float[] tangentDeltas() {
        return tangentDeltas == null ? null : tangentDeltas.clone();
    }
}
