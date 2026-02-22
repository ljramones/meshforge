package org.meshforge.pack.packer;

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

    public long totalNs() {
        return totalNs;
    }

    public long resolveAttributesNs() {
        return resolveAttributesNs;
    }

    public long layoutNs() {
        return layoutNs;
    }

    public long vertexWriteNs() {
        return vertexWriteNs;
    }

    public long positionWriteNs() {
        return positionWriteNs;
    }

    public long normalPackNs() {
        return normalPackNs;
    }

    public long tangentPackNs() {
        return tangentPackNs;
    }

    public long uvPackNs() {
        return uvPackNs;
    }

    public long colorPackNs() {
        return colorPackNs;
    }

    public long skinPackNs() {
        return skinPackNs;
    }

    public long indexPackNs() {
        return indexPackNs;
    }

    public long submeshCopyNs() {
        return submeshCopyNs;
    }

    public int vertexCount() {
        return vertexCount;
    }

    public int indexCount() {
        return indexCount;
    }

    public int strideBytes() {
        return strideBytes;
    }
}
