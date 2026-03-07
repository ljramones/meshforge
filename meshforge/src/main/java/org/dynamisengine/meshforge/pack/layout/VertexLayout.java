package org.dynamisengine.meshforge.pack.layout;

import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.VertexFormat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable packed-vertex layout metadata.
 */
public final class VertexLayout {
    /**
     * Layout entry describing one packed attribute stream segment.
     *
     * @param key attribute key
     * @param format packed format
     * @param offsetBytes byte offset from the start of a vertex
     */
    public record Entry(AttributeKey key, VertexFormat format, int offsetBytes) {
    }

    private final int strideBytes;
    private final Map<AttributeKey, Entry> entries;

    /**
     * Creates a layout from stride and per-attribute entries.
     *
     * @param strideBytes total vertex stride in bytes
     * @param entries entry map keyed by attribute
     */
    public VertexLayout(int strideBytes, LinkedHashMap<AttributeKey, Entry> entries) {
        this.strideBytes = strideBytes;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /**
     * Returns the vertex stride in bytes.
     *
     * @return vertex stride
     */
    public int strideBytes() {
        return strideBytes;
    }

    /**
     * Returns all layout entries by attribute key.
     *
     * @return immutable entry map
     */
    public Map<AttributeKey, Entry> entries() {
        return entries;
    }

    /**
     * Looks up one entry by key.
     *
     * @param key attribute key
     * @return entry or {@code null} when absent
     */
    public Entry entry(AttributeKey key) {
        return entries.get(key);
    }
}
