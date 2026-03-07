package org.dynamisengine.meshforge.ops.pipeline;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable key-value scratch context shared across pipeline operations.
 */
public final class MeshContext {
    private final Map<String, Object> scratch = new HashMap<>();

    /**
     * Creates an empty context.
     */
    public MeshContext() {
    }

    /**
     * Returns a value from the context.
     *
     * @param key lookup key
     * @return stored value or {@code null} when absent
     */
    public Object get(String key) {
        return scratch.get(key);
    }

    /**
     * Stores a value in the context.
     *
     * @param key storage key
     * @param value value to store
     */
    public void put(String key, Object value) {
        scratch.put(key, value);
    }
}
