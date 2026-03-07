package org.dynamisengine.meshforge.core.attr;

/**
 * Public enum VertexFormat.
 */
public enum VertexFormat {
    // Float formats
    F32x1(ScalarKind.FLOAT, 1, 4, false),
    F32x2(ScalarKind.FLOAT, 2, 8, false),
    F32x3(ScalarKind.FLOAT, 3, 12, false),
    F32x4(ScalarKind.FLOAT, 4, 16, false),

    // Int formats (authoring)
    I32x1(ScalarKind.INT, 1, 4, false),
    I32x2(ScalarKind.INT, 2, 8, false),
    I32x3(ScalarKind.INT, 3, 12, false),
    I32x4(ScalarKind.INT, 4, 16, false),

    // Short formats (common packed / half style)
    F16x2(ScalarKind.SHORT, 2, 4, false),
    I16x2(ScalarKind.SHORT, 2, 4, false),
    I16x4(ScalarKind.SHORT, 4, 8, false),
    U16x4(ScalarKind.SHORT, 4, 8, false),
    SNORM16x4(ScalarKind.SHORT, 4, 8, true),

    // Byte formats (colors/joints packed later)
    I8x4(ScalarKind.BYTE, 4, 4, false),
    U8x4(ScalarKind.BYTE, 4, 4, false),

    // Normalized packed
    UNORM8x4(ScalarKind.INT, 4, 4, true),
    SNORM8x4(ScalarKind.INT, 4, 4, true),
    OCTA_SNORM16x2(ScalarKind.INT, 2, 4, true);

    public enum ScalarKind { FLOAT, INT, SHORT, BYTE }

    private final ScalarKind kind;
    private final int components;
    private final int bytes;
    private final boolean normalized;

    VertexFormat(ScalarKind kind, int components, int bytes, boolean normalized) {
        this.kind = kind;
        this.components = components;
        this.bytes = bytes;
        this.normalized = normalized;
    }

    /**
     * Executes kind.
     * @return resulting value
     */
    public ScalarKind kind() {
        return kind;
    }

    /**
     * Executes components.
     * @return resulting value
     */
    public int components() {
        return components;
    }

    /**
     * Executes bytesPerVertex.
     * @return resulting value
     */
    public int bytesPerVertex() {
        return bytes;
    }

    /**
     * Executes normalized.
     * @return resulting value
     */
    public boolean normalized() {
        return normalized;
    }
}
