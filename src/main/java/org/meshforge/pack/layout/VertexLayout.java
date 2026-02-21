package org.meshforge.pack.layout;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.VertexFormat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VertexLayout {
    public record Entry(AttributeKey key, VertexFormat format, int offsetBytes) {
    }

    private final int strideBytes;
    private final Map<AttributeKey, Entry> entries;

    public VertexLayout(int strideBytes, LinkedHashMap<AttributeKey, Entry> entries) {
        this.strideBytes = strideBytes;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    public int strideBytes() {
        return strideBytes;
    }

    public Map<AttributeKey, Entry> entries() {
        return entries;
    }

    public Entry entry(AttributeKey key) {
        return entries.get(key);
    }
}
