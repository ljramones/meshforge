package org.meshforge.api;

import org.meshforge.pack.spec.PackSpec;

/**
 * Entry point for packer presets and helpers.
 */
public final class Packers {

    private Packers() {
    }

    public static PackSpec realtime() {
        return PackSpec.realtime();
    }

    public static PackSpec debug() {
        return PackSpec.debug();
    }
}
