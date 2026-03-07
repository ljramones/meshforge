package org.dynamisengine.meshforge.core.mesh;

/**
 * Per-vertex morph target deltas for base-mesh deformation.
 * POSITION deltas are required; NORMAL/TANGENT deltas are optional.
 */
public final class MorphTarget {
    private final String name;
    private final float[] positionDeltas;
    private final float[] normalDeltas;
    private final float[] tangentDeltas;

    /**
     * Creates a new {@code MorphTarget} instance.
     * @param name parameter value
     * @param positionDeltas parameter value
     * @param normalDeltas parameter value
     * @param tangentDeltas parameter value
     */
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

    /**
     * Executes name.
     * @return resulting value
     */
    public String name() {
        return name;
    }

    /**
     * Executes positionDeltas.
     * @return resulting value
     */
    public float[] positionDeltas() {
        return positionDeltas.clone();
    }

    /**
     * Executes normalDeltas.
     * @return resulting value
     */
    public float[] normalDeltas() {
        return normalDeltas == null ? null : normalDeltas.clone();
    }

    /**
     * Executes tangentDeltas.
     * @return resulting value
     */
    public float[] tangentDeltas() {
        return tangentDeltas == null ? null : tangentDeltas.clone();
    }
}
