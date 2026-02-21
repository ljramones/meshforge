package org.meshforge.ops.pipeline;

import java.util.HashMap;
import java.util.Map;

public final class MeshContext {
    private final Map<String, Object> scratch = new HashMap<>();

    public Object get(String key) {
        return scratch.get(key);
    }

    public void put(String key, Object value) {
        scratch.put(key, value);
    }
}
