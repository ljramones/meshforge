package org.dynamisengine.meshforge.pack.buffer;

import java.util.List;

final class SimpleMeshletBufferView implements MeshletBufferView {
    private final List<Meshlet> meshlets;

    SimpleMeshletBufferView(List<Meshlet> meshlets) {
        this.meshlets = List.copyOf(meshlets);
    }

    @Override
    public int meshletCount() {
        return meshlets.size();
    }

    @Override
    public Meshlet meshlet(int index) {
        return meshlets.get(index);
    }

    @Override
    public List<Meshlet> asList() {
        return meshlets;
    }
}
