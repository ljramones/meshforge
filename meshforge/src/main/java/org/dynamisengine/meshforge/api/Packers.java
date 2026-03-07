package org.dynamisengine.meshforge.api;

import org.dynamisengine.meshforge.pack.spec.PackSpec;

/**
 * Entry point for packer presets and helpers.
 */
public final class Packers {

    private Packers() {
    }

    /**
     * Returns realtime.
     * @return resulting value
     */
    public static PackSpec realtime() {
        return PackSpec.realtime();
    }

    /**
     * Returns realtimeFast.
     * @return resulting value
     */
    public static PackSpec realtimeFast() {
        return PackSpec.realtimeFast();
    }

    /**
     * Returns debug.
     * @return resulting value
     */
    public static PackSpec debug() {
        return PackSpec.debug();
    }

    /**
     * Returns realtimeMinimal.
     * @return resulting value
     */
    public static PackSpec realtimeMinimal() {
        return PackSpec.realtimeMinimal();
    }

    /**
     * Returns realtimeWithMeshlets.
     * @return resulting value
     */
    public static PackSpec realtimeWithMeshlets() {
        return PackSpec.realtimeWithMeshlets();
    }

    /**
     * Returns realtimeWithOctaNormals.
     * @return resulting value
     */
    public static PackSpec realtimeWithOctaNormals() {
        return PackSpec.realtimeWithOctaNormals();
    }
}
