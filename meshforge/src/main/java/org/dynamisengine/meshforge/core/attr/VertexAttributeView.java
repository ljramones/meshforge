package org.dynamisengine.meshforge.core.attr;

/**
 * Public interface VertexAttributeView.
 */
public interface VertexAttributeView {
    AttributeKey key();

    VertexFormat format();

    int vertexCount();

    float getFloat(int vertexIndex, int component);

    void setFloat(int vertexIndex, int component, float value);

    int getInt(int vertexIndex, int component);

    void setInt(int vertexIndex, int component, int value);

    default void set2f(int i, float x, float y) {
        setFloat(i, 0, x);
        setFloat(i, 1, y);
    }

    default void set3f(int i, float x, float y, float z) {
        setFloat(i, 0, x);
        setFloat(i, 1, y);
        setFloat(i, 2, z);
    }

    default void set4f(int i, float x, float y, float z, float w) {
        setFloat(i, 0, x);
        setFloat(i, 1, y);
        setFloat(i, 2, z);
        setFloat(i, 3, w);
    }

    default float[] rawFloatArrayOrNull() {
        return null;
    }

    default int[] rawIntArrayOrNull() {
        return null;
    }
}
