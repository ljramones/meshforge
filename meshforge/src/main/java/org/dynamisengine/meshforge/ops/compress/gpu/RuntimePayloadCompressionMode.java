package org.dynamisengine.meshforge.ops.compress.gpu;

/**
 * Optional runtime payload compression mode for GPU handoff payload experiments.
 */
public enum RuntimePayloadCompressionMode {
    /**
     * Uncompressed payload bytes.
     */
    NONE,
    /**
     * Deflate-compressed payload bytes.
     */
    DEFLATE
}

