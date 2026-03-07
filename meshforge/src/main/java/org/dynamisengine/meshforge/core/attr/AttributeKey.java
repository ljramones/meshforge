package org.dynamisengine.meshforge.core.attr;

import java.util.Objects;

/**
 * Public class AttributeKey.
 */
public final class AttributeKey {
    private final AttributeSemantic semantic;
    private final int setIndex;

    /**
     * Creates a new {@code AttributeKey} instance.
     * @param semantic parameter value
     * @param setIndex parameter value
     */
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

    /**
     * Executes semantic.
     * @return resulting value
     */
    public AttributeSemantic semantic() {
        return semantic;
    }

    /**
     * Sets setIndex.
     * @return resulting value
     */
    public int setIndex() {
        return setIndex;
    }

    @Override
    /**
     * Executes equals.
     * @param o parameter value
     * @return resulting value
     */
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
    /**
     * Checks whether this instance has hashCode.
     * @return resulting value
     */
    public int hashCode() {
        return Objects.hash(semantic, setIndex);
    }

    @Override
    /**
     * Returns toString.
     * @return resulting value
     */
    public String toString() {
        return semantic + (setIndex == 0 ? "" : Integer.toString(setIndex));
    }
}
