package org.dynamisengine.meshforge.loader;

import org.dynamisengine.meshforge.core.attr.AttributeSemantic;
import org.dynamisengine.meshforge.core.mesh.MeshData;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

/**
 * Package-private utility class for glTF accessor reading and vertex attribute copying.
 */
final class GltfAccessorOps {
    static final int COMPONENT_FLOAT = 5126;
    static final int COMPONENT_UNSIGNED_INT = 5125;
    static final int COMPONENT_UNSIGNED_SHORT = 5123;
    static final int COMPONENT_UNSIGNED_BYTE = 5121;

    private GltfAccessorOps() {
    }

    record ViewData(byte[] data, int byteStride) {
    }

    record AccessorData(
        byte[] data,
        ByteBuffer buffer,
        int byteOffset,
        int count,
        int componentType,
        int components,
        int componentSize,
        int byteStride,
        boolean normalized
    ) {
    }

    static AccessorData readAccessor(
        List<Map<String, Object>> accessors,
        List<Map<String, Object>> bufferViews,
        List<byte[]> buffers,
        int accessorIndex,
        MeshLoadOptions options
    ) throws IOException {
        if (accessorIndex < 0 || accessorIndex >= accessors.size()) {
            throw new IOException("Accessor index out of range: " + accessorIndex);
        }
        Map<String, Object> accessor = accessors.get(accessorIndex);
        Integer count = GltfMeshLoader.numberAsInt(accessor.get("count"), "accessor.count");
        Integer componentType = GltfMeshLoader.numberAsInt(accessor.get("componentType"), "accessor.componentType");
        String type = GltfMeshLoader.asString(accessor.get("type"), "accessor.type");
        Integer viewIndex = GltfMeshLoader.numberAsInt(accessor.get("bufferView"), "accessor.bufferView");
        int accessorByteOffset = GltfMeshLoader.numberAsInt(accessor.get("byteOffset"), "accessor.byteOffset", 0);
        if (count == null || componentType == null || viewIndex == null) {
            throw new IOException("Accessor is missing required fields");
        }
        if (count < 0) {
            throw new IOException("Accessor count must be >= 0");
        }
        if (accessorByteOffset < 0) {
            throw new IOException("Accessor byteOffset must be >= 0");
        }

        ViewData viewData = GltfBufferOps.resolveViewData(bufferViews, buffers, viewIndex, options);
        int compCount = componentCount(type);
        int compSize = componentSize(componentType);
        int packedStride = compCount * compSize;
        int stride = viewData.byteStride() > 0 ? viewData.byteStride() : packedStride;
        if (stride < packedStride) {
            throw new IOException("Accessor stride is smaller than packed element size");
        }
        ensureAccessorRangeFits(viewData.data().length, accessorByteOffset, count, stride, packedStride);
        boolean normalized = Boolean.TRUE.equals(accessor.get("normalized"));
        return new AccessorData(
            viewData.data(),
            ByteBuffer.wrap(viewData.data()).order(ByteOrder.LITTLE_ENDIAN),
            accessorByteOffset,
            count,
            componentType,
            compCount,
            compSize,
            stride,
            normalized
        );
    }

    static float readFloatComponent(AccessorData accessor, int index, int component) throws IOException {
        int off = accessor.byteOffset() + index * accessor.byteStride() + component * accessor.componentSize();
        if (off < 0 || off + accessor.componentSize() > accessor.data().length) {
            throw new IOException("Accessor read out of bounds");
        }
        return switch (accessor.componentType()) {
            case COMPONENT_FLOAT -> accessor.buffer().getFloat(off);
            default -> throw new IOException("Unsupported float accessor componentType: " + accessor.componentType());
        };
    }

    static int readUnsignedIntComponent(AccessorData accessor, int index, int component) throws IOException {
        int off = accessor.byteOffset() + index * accessor.byteStride() + component * accessor.componentSize();
        if (off < 0 || off + accessor.componentSize() > accessor.data().length) {
            throw new IOException("Index accessor read out of bounds");
        }
        return switch (accessor.componentType()) {
            case COMPONENT_UNSIGNED_INT -> accessor.buffer().getInt(off);
            case COMPONENT_UNSIGNED_SHORT -> accessor.buffer().getShort(off) & 0xFFFF;
            case COMPONENT_UNSIGNED_BYTE -> accessor.buffer().get(off) & 0xFF;
            default -> throw new IOException("Unsupported index componentType: " + accessor.componentType());
        };
    }

    static float readWeightComponent(AccessorData accessor, int index, int component) throws IOException {
        int off = accessor.byteOffset() + index * accessor.byteStride() + component * accessor.componentSize();
        if (off < 0 || off + accessor.componentSize() > accessor.data().length) {
            throw new IOException("Accessor read out of bounds");
        }
        return switch (accessor.componentType()) {
            case COMPONENT_FLOAT -> accessor.buffer().getFloat(off);
            case COMPONENT_UNSIGNED_BYTE -> {
                int v = accessor.buffer().get(off) & 0xFF;
                yield accessor.normalized() ? v / 255.0f : (float) v;
            }
            case COMPONENT_UNSIGNED_SHORT -> {
                int v = accessor.buffer().getShort(off) & 0xFFFF;
                yield accessor.normalized() ? v / 65535.0f : (float) v;
            }
            default -> throw new IOException("Unsupported WEIGHTS_0 componentType: " + accessor.componentType());
        };
    }

    static void copyFloatAttribute(
        MeshData mesh,
        AttributeSemantic semantic,
        int set,
        AccessorData accessor,
        int expectedComponents
    ) throws IOException {
        if (accessor.components() != expectedComponents) {
            throw new IOException(
                semantic + " accessor components mismatch: expected " + expectedComponents + " got " + accessor.components());
        }
        var view = mesh.attribute(semantic, set);
        for (int i = 0; i < accessor.count(); i++) {
            for (int c = 0; c < expectedComponents; c++) {
                view.setFloat(i, c, readFloatComponent(accessor, i, c));
            }
        }
    }

    static void copyJointAttribute(
        MeshData mesh,
        AccessorData accessor,
        Integer skinJointCount
    ) throws IOException {
        if (accessor.components() != 4) {
            throw new IOException("JOINTS_0 accessor components mismatch: expected 4 got " + accessor.components());
        }
        var view = mesh.attribute(AttributeSemantic.JOINTS, 0);
        for (int i = 0; i < accessor.count(); i++) {
            for (int c = 0; c < 4; c++) {
                int joint = readUnsignedIntComponent(accessor, i, c);
                if (skinJointCount != null && joint >= skinJointCount) {
                    throw new IOException(
                        "JOINTS_0 value out of range at vertex " + i + " component " + c
                            + ": " + joint + " (skin jointCount=" + skinJointCount + ")"
                    );
                }
                view.setInt(i, c, joint);
            }
        }
    }

    static void copyWeightsAttribute(MeshData mesh, AccessorData accessor) throws IOException {
        if (accessor.components() != 4) {
            throw new IOException("WEIGHTS_0 accessor components mismatch: expected 4 got " + accessor.components());
        }
        var view = mesh.attribute(AttributeSemantic.WEIGHTS, 0);
        for (int i = 0; i < accessor.count(); i++) {
            float w0 = readWeightComponent(accessor, i, 0);
            float w1 = readWeightComponent(accessor, i, 1);
            float w2 = readWeightComponent(accessor, i, 2);
            float w3 = readWeightComponent(accessor, i, 3);
            float sum = w0 + w1 + w2 + w3;
            if (sum > 1.0e-8f) {
                float inv = 1.0f / sum;
                w0 *= inv;
                w1 *= inv;
                w2 *= inv;
                w3 *= inv;
            } else {
                w0 = 1.0f;
                w1 = 0.0f;
                w2 = 0.0f;
                w3 = 0.0f;
            }
            view.setFloat(i, 0, w0);
            view.setFloat(i, 1, w1);
            view.setFloat(i, 2, w2);
            view.setFloat(i, 3, w3);
        }
    }

    static void putIfPresent(
        Map<String, Object> attrs,
        String key,
        AttributeSemantic semantic,
        Map<AttributeSemantic, Integer> out
    ) throws IOException {
        Integer idx = GltfMeshLoader.numberAsInt(attrs.get(key), key + " accessor index");
        if (idx != null) {
            out.put(semantic, idx);
        }
    }

    static void ensureAccessorRangeFits(
        int bufferLength,
        int accessorByteOffset,
        int count,
        int stride,
        int packedStride
    ) throws IOException {
        if (count == 0) {
            if (accessorByteOffset > bufferLength) {
                throw new IOException("Accessor byteOffset is out of bounds");
            }
            return;
        }
        long lastOffset = (long) accessorByteOffset + (long) (count - 1) * stride;
        long requiredEnd = lastOffset + packedStride;
        if (requiredEnd > bufferLength) {
            throw new IOException("Accessor range is out of bounds");
        }
    }

    static int componentCount(String type) throws IOException {
        return switch (type) {
            case "SCALAR" -> 1;
            case "VEC2" -> 2;
            case "VEC3" -> 3;
            case "VEC4" -> 4;
            default -> throw new IOException("Unsupported accessor type: " + type);
        };
    }

    static int componentSize(int componentType) throws IOException {
        return switch (componentType) {
            case COMPONENT_UNSIGNED_BYTE -> 1;
            case COMPONENT_UNSIGNED_SHORT -> 2;
            case COMPONENT_UNSIGNED_INT, COMPONENT_FLOAT -> 4;
            default -> throw new IOException("Unsupported accessor componentType: " + componentType);
        };
    }
}
