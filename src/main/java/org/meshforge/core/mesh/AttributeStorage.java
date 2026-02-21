package org.meshforge.core.mesh;

import org.meshforge.core.attr.AttributeKey;
import org.meshforge.core.attr.VertexAttributeView;
import org.meshforge.core.attr.VertexFormat;

sealed abstract class AttributeStorage permits FloatStorage, IntStorage, ShortStorage, ByteStorage {
    final AttributeKey key;
    final VertexFormat format;
    final int vertexCount;
    final int components;

    AttributeStorage(AttributeKey key, VertexFormat format, int vertexCount) {
        this.key = key;
        this.format = format;
        this.vertexCount = vertexCount;
        this.components = format.components();
    }

    abstract VertexAttributeView view();

    static AttributeStorage allocate(AttributeKey key, VertexFormat format, int vertexCount) {
        int elements = Math.multiplyExact(vertexCount, format.components());
        return switch (format.kind()) {
            case FLOAT -> new FloatStorage(key, format, vertexCount, new float[elements]);
            case INT -> new IntStorage(key, format, vertexCount, new int[elements]);
            case SHORT -> new ShortStorage(key, format, vertexCount, new short[elements]);
            case BYTE -> new ByteStorage(key, format, vertexCount, new byte[elements]);
        };
    }
}

final class FloatStorage extends AttributeStorage {
    private final VertexAttributeView view;

    FloatStorage(AttributeKey key, VertexFormat format, int vertexCount, float[] data) {
        super(key, format, vertexCount);
        this.view = new FloatAttributeView(key, format, vertexCount, components, data);
    }

    @Override
    VertexAttributeView view() {
        return view;
    }
}

final class IntStorage extends AttributeStorage {
    private final VertexAttributeView view;

    IntStorage(AttributeKey key, VertexFormat format, int vertexCount, int[] data) {
        super(key, format, vertexCount);
        this.view = new IntAttributeView(key, format, vertexCount, components, data);
    }

    @Override
    VertexAttributeView view() {
        return view;
    }
}

final class ShortStorage extends AttributeStorage {
    private final VertexAttributeView view;

    ShortStorage(AttributeKey key, VertexFormat format, int vertexCount, short[] data) {
        super(key, format, vertexCount);
        this.view = new ShortAttributeView(key, format, vertexCount, components, data);
    }

    @Override
    VertexAttributeView view() {
        return view;
    }
}

final class ByteStorage extends AttributeStorage {
    private final VertexAttributeView view;

    ByteStorage(AttributeKey key, VertexFormat format, int vertexCount, byte[] data) {
        super(key, format, vertexCount);
        this.view = new ByteAttributeView(key, format, vertexCount, components, data);
    }

    @Override
    VertexAttributeView view() {
        return view;
    }
}
