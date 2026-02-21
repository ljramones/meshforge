package org.meshforge.core.attr;

import java.util.Objects;

public final class AttributeKey {
    private final AttributeSemantic semantic;
    private final int setIndex;

    public AttributeKey(AttributeSemantic semantic, int setIndex) {
        if (semantic == null) {
            throw new NullPointerException("semantic");
        }
        if (setIndex < 0 || setIndex > 255) {
            throw new IllegalArgumentException("setIndex must be 0..255");
        }
        this.semantic = semantic;
        this.setIndex = setIndex;
    }

    public AttributeSemantic semantic() {
        return semantic;
    }

    public int setIndex() {
        return setIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AttributeKey key)) {
            return false;
        }
        return setIndex == key.setIndex && semantic == key.semantic;
    }

    @Override
    public int hashCode() {
        return Objects.hash(semantic, setIndex);
    }

    @Override
    public String toString() {
        return semantic + (setIndex == 0 ? "" : Integer.toString(setIndex));
    }
}
