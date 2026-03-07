package org.dynamisengine.meshforge.pack.packer;

/**
 * Optional timing/profile sink for {@link MeshPacker}.
 * All durations are nanoseconds.
 */
public final class PackProfile {
    private long totalNs;
    private long resolveAttributesNs;
    private long layoutNs;
    private long vertexWriteNs;
    private long positionWriteNs;
    private long normalPackNs;
    private long tangentPackNs;
    private long uvPackNs;
    private long colorPackNs;
    private long skinPackNs;
    private long indexPackNs;
    private long submeshCopyNs;
    private int vertexCount;
    private int indexCount;
    private int strideBytes;

    /**
     * Creates an empty pack profile.
     */
    public PackProfile() {
    }

    void reset() {
        totalNs = 0L;
        resolveAttributesNs = 0L;
        layoutNs = 0L;
        vertexWriteNs = 0L;
        positionWriteNs = 0L;
        normalPackNs = 0L;
        tangentPackNs = 0L;
        uvPackNs = 0L;
        colorPackNs = 0L;
        skinPackNs = 0L;
        indexPackNs = 0L;
        submeshCopyNs = 0L;
        vertexCount = 0;
        indexCount = 0;
        strideBytes = 0;
    }

    void setResolveAttributesNs(long value) {
        resolveAttributesNs = value;
    }

    void setLayoutNs(long value) {
        layoutNs = value;
    }

    void setVertexWriteNs(long value) {
        vertexWriteNs = value;
    }

    void setIndexPackNs(long value) {
        indexPackNs = value;
    }

    void setPositionWriteNs(long value) {
        positionWriteNs = value;
    }

    void setNormalPackNs(long value) {
        normalPackNs = value;
    }

    void setTangentPackNs(long value) {
        tangentPackNs = value;
    }

    void setUvPackNs(long value) {
        uvPackNs = value;
    }

    void setColorPackNs(long value) {
        colorPackNs = value;
    }

    void setSkinPackNs(long value) {
        skinPackNs = value;
    }

    void setSubmeshCopyNs(long value) {
        submeshCopyNs = value;
    }

    void setTotalNs(long value) {
        totalNs = value;
    }

    void setVertexCount(int value) {
        vertexCount = value;
    }

    void setIndexCount(int value) {
        indexCount = value;
    }

    void setStrideBytes(int value) {
        strideBytes = value;
    }

    /**
     * Returns totalNs.
     * @return resulting value
     */
    public long totalNs() {
        return totalNs;
    }

    /**
     * Returns resolveAttributesNs.
     * @return resulting value
     */
    public long resolveAttributesNs() {
        return resolveAttributesNs;
    }

    /**
     * Returns layoutNs.
     * @return resulting value
     */
    public long layoutNs() {
        return layoutNs;
    }

    /**
     * Returns vertexWriteNs.
     * @return resulting value
     */
    public long vertexWriteNs() {
        return vertexWriteNs;
    }

    /**
     * Returns positionWriteNs.
     * @return resulting value
     */
    public long positionWriteNs() {
        return positionWriteNs;
    }

    /**
     * Returns normalPackNs.
     * @return resulting value
     */
    public long normalPackNs() {
        return normalPackNs;
    }

    /**
     * Returns tangentPackNs.
     * @return resulting value
     */
    public long tangentPackNs() {
        return tangentPackNs;
    }

    /**
     * Returns uvPackNs.
     * @return resulting value
     */
    public long uvPackNs() {
        return uvPackNs;
    }

    /**
     * Returns colorPackNs.
     * @return resulting value
     */
    public long colorPackNs() {
        return colorPackNs;
    }

    /**
     * Returns skinPackNs.
     * @return resulting value
     */
    public long skinPackNs() {
        return skinPackNs;
    }

    /**
     * Returns indexPackNs.
     * @return resulting value
     */
    public long indexPackNs() {
        return indexPackNs;
    }

    /**
     * Returns submeshCopyNs.
     * @return resulting value
     */
    public long submeshCopyNs() {
        return submeshCopyNs;
    }

    /**
     * Returns vertexCount.
     * @return resulting value
     */
    public int vertexCount() {
        return vertexCount;
    }

    /**
     * Returns indexCount.
     * @return resulting value
     */
    public int indexCount() {
        return indexCount;
    }

    /**
     * Returns strideBytes.
     * @return resulting value
     */
    public int strideBytes() {
        return strideBytes;
    }
}
