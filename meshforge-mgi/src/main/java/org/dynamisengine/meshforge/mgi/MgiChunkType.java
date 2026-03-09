package org.dynamisengine.meshforge.mgi;

import java.util.HashMap;
import java.util.Map;

/**
 * Known MGI chunk types.
 */
public enum MgiChunkType {
    MESH_TABLE(0x1001, true),
    ATTRIBUTE_SCHEMA(0x1002, true),
    VERTEX_STREAMS(0x1003, true),
    INDEX_DATA(0x1004, true),
    SUBMESH_TABLE(0x1005, true),
    BOUNDS(0x1006, false),
    METADATA(0x1007, false),
    MESHLET_DESCRIPTORS(0x1101, false),
    MESHLET_VERTEX_REMAP(0x1102, false),
    MESHLET_TRIANGLES(0x1103, false),
    MESHLET_BOUNDS(0x1104, false),
    MESHLET_LOD_LEVELS(0x1105, false),
    MESHLET_STREAM_UNITS(0x1106, false),
    RAY_TRACING_REGIONS(0x1201, false),
    TESSELLATION_REGIONS(0x1301, false);

    private static final Map<Integer, MgiChunkType> BY_ID = new HashMap<>();

    static {
        for (MgiChunkType type : values()) {
            BY_ID.put(type.id, type);
        }
    }

    private final int id;
    private final boolean required;

    MgiChunkType(int id, boolean required) {
        this.id = id;
        this.required = required;
    }

    public int id() {
        return id;
    }

    public boolean required() {
        return required;
    }

    public static MgiChunkType fromId(int id) {
        return BY_ID.get(id);
    }
}
