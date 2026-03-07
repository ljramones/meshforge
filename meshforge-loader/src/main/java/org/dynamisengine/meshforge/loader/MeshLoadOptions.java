package org.dynamisengine.meshforge.loader;

/**
 * Loader-side options controlling import behavior.
 */
public final class MeshLoadOptions {
    private static final MeshLoadOptions DEFAULTS = builder().build();

    private final boolean meshoptDecodeEnabled;

    private MeshLoadOptions(Builder builder) {
        this.meshoptDecodeEnabled = builder.meshoptDecodeEnabled;
    }

    /**
     * Executes defaults.
     * @return resulting value
     */
    public static MeshLoadOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Creates builder.
     * @return resulting value
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Executes meshoptDecodeEnabled.
     * @return resulting value
     */
    public boolean meshoptDecodeEnabled() {
        return meshoptDecodeEnabled;
    }

    public static final class Builder {
        private boolean meshoptDecodeEnabled = true;

        public Builder meshoptDecodeEnabled(boolean enabled) {
            this.meshoptDecodeEnabled = enabled;
            return this;
        }

        public MeshLoadOptions build() {
            return new MeshLoadOptions(this);
        }
    }
}

