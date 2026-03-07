package org.dynamisengine.meshforge.core.mesh;

import org.dynamisengine.meshforge.core.attr.AttributeKey;
import org.dynamisengine.meshforge.core.attr.VertexAttributeView;
import org.dynamisengine.meshforge.core.attr.VertexFormat;

final class FloatAttributeView implements VertexAttributeView {
    private final AttributeKey key;
    private final VertexFormat format;
    private final int vertexCount;
    private final int components;
    private final float[] data;

    FloatAttributeView(AttributeKey key, VertexFormat format, int vertexCount, int components, float[] data) {
        this.key = key;
        this.format = format;
        this.vertexCount = vertexCount;
        this.components = components;
        this.data = data;
    }

    @Override
    public AttributeKey key() {
        return key;
    }

    @Override
    public VertexFormat format() {
        return format;
    }

    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public float getFloat(int vertexIndex, int component) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        return data[off];
    }

    @Override
    public void setFloat(int vertexIndex, int component, float value) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        data[off] = value;
    }

    @Override
    public int getInt(int vertexIndex, int component) {
        return (int) getFloat(vertexIndex, component);
    }

    @Override
    public void setInt(int vertexIndex, int component, int value) {
        setFloat(vertexIndex, component, value);
    }

    @Override
    public float[] rawFloatArrayOrNull() {
        return data;
    }
}

final class IntAttributeView implements VertexAttributeView {
    private final AttributeKey key;
    private final VertexFormat format;
    private final int vertexCount;
    private final int components;
    private final int[] data;

    IntAttributeView(AttributeKey key, VertexFormat format, int vertexCount, int components, int[] data) {
        this.key = key;
        this.format = format;
        this.vertexCount = vertexCount;
        this.components = components;
        this.data = data;
    }

    @Override
    public AttributeKey key() {
        return key;
    }

    @Override
    public VertexFormat format() {
        return format;
    }

    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public float getFloat(int vertexIndex, int component) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        return data[off];
    }

    @Override
    public void setFloat(int vertexIndex, int component, float value) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        data[off] = (int) value;
    }

    @Override
    public int getInt(int vertexIndex, int component) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        return data[off];
    }

    @Override
    public void setInt(int vertexIndex, int component, int value) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        data[off] = value;
    }

    @Override
    public int[] rawIntArrayOrNull() {
        return data;
    }
}

final class ShortAttributeView implements VertexAttributeView {
    private final AttributeKey key;
    private final VertexFormat format;
    private final int vertexCount;
    private final int components;
    private final short[] data;

    ShortAttributeView(AttributeKey key, VertexFormat format, int vertexCount, int components, short[] data) {
        this.key = key;
        this.format = format;
        this.vertexCount = vertexCount;
        this.components = components;
        this.data = data;
    }

    @Override
    public AttributeKey key() {
        return key;
    }

    @Override
    public VertexFormat format() {
        return format;
    }

    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public float getFloat(int vertexIndex, int component) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        return data[off];
    }

    @Override
    public void setFloat(int vertexIndex, int component, float value) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        data[off] = (short) value;
    }

    @Override
    public int getInt(int vertexIndex, int component) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        return data[off];
    }

    @Override
    public void setInt(int vertexIndex, int component, int value) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        data[off] = (short) value;
    }
}

final class ByteAttributeView implements VertexAttributeView {
    private final AttributeKey key;
    private final VertexFormat format;
    private final int vertexCount;
    private final int components;
    private final byte[] data;

    ByteAttributeView(AttributeKey key, VertexFormat format, int vertexCount, int components, byte[] data) {
        this.key = key;
        this.format = format;
        this.vertexCount = vertexCount;
        this.components = components;
        this.data = data;
    }

    @Override
    public AttributeKey key() {
        return key;
    }

    @Override
    public VertexFormat format() {
        return format;
    }

    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public float getFloat(int vertexIndex, int component) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        return data[off];
    }

    @Override
    public void setFloat(int vertexIndex, int component, float value) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        data[off] = (byte) value;
    }

    @Override
    public int getInt(int vertexIndex, int component) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        return data[off];
    }

    @Override
    public void setInt(int vertexIndex, int component, int value) {
        int off = ViewChecks.offset(vertexIndex, component, vertexCount, components);
        data[off] = (byte) value;
    }
}

final class ViewChecks {
    private ViewChecks() {
    }

    static int offset(int vertexIndex, int component, int vertexCount, int components) {
        if (vertexIndex < 0 || vertexIndex >= vertexCount) {
            throw new IndexOutOfBoundsException("vertexIndex " + vertexIndex);
        }
        if (component < 0 || component >= components) {
            throw new IndexOutOfBoundsException("component " + component);
        }
        return vertexIndex * components + component;
    }
}
