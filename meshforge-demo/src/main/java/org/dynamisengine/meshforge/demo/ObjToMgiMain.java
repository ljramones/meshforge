package org.dynamisengine.meshforge.demo;

import org.dynamisengine.meshforge.core.mesh.MeshData;
import org.dynamisengine.meshforge.core.mesh.Submesh;
import org.dynamisengine.meshforge.loader.MeshLoaders;
import org.dynamisengine.meshforge.mgi.MgiMeshletBounds;
import org.dynamisengine.meshforge.mgi.MgiMeshletData;
import org.dynamisengine.meshforge.mgi.MgiMeshletDescriptor;
import org.dynamisengine.meshforge.mgi.MgiMeshDataCodec;
import org.dynamisengine.meshforge.mgi.MgiStaticMesh;
import org.dynamisengine.meshforge.mgi.MgiStaticMeshCodec;
import org.dynamisengine.meshforge.ops.optimize.MeshletClusters;
import org.dynamisengine.meshforge.pack.buffer.Meshlet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts OBJ fixtures to MGI sidecars.
 */
public final class ObjToMgiMain {
    private ObjToMgiMain() {
    }

    public static void main(String[] args) throws Exception {
        Path input = Path.of("fixtures", "baseline");
        boolean overwrite = false;
        boolean withMeshlets = false;
        int meshletMaxVerts = 64;
        int meshletMaxTris = 64;

        for (String arg : args) {
            if (arg.startsWith("--input=")) {
                input = Path.of(arg.substring("--input=".length()));
            } else if (arg.equals("--overwrite")) {
                overwrite = true;
            } else if (arg.equals("--with-meshlets")) {
                withMeshlets = true;
            } else if (arg.startsWith("--meshlet-max-verts=")) {
                meshletMaxVerts = parsePositive(arg.substring("--meshlet-max-verts=".length()), "meshlet-max-verts");
            } else if (arg.startsWith("--meshlet-max-tris=")) {
                meshletMaxTris = parsePositive(arg.substring("--meshlet-max-tris=".length()), "meshlet-max-tris");
            }
        }

        if (!Files.exists(input)) {
            throw new IllegalArgumentException("Input path does not exist: " + input.toAbsolutePath());
        }

        List<Path> objFiles;
        if (Files.isRegularFile(input)) {
            objFiles = List.of(input);
        } else {
            try (var walk = Files.walk(input)) {
                objFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".obj"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            }
        }

        if (objFiles.isEmpty()) {
            System.out.println("No OBJ files found.");
            return;
        }

        MeshLoaders loaders = MeshLoaders.defaultsFast();
        MgiMeshDataCodec codec = new MgiMeshDataCodec();
        MgiStaticMeshCodec staticCodec = new MgiStaticMeshCodec();

        int converted = 0;
        int skipped = 0;
        for (Path obj : objFiles) {
            Path mgi = mgiPathFor(obj, withMeshlets);
            if (Files.exists(mgi) && !overwrite) {
                skipped++;
                continue;
            }

            MeshData mesh = loaders.load(obj);
            byte[] bytes = withMeshlets
                ? staticCodec.write(toMgiWithMeshlets(mesh, meshletMaxVerts, meshletMaxTris))
                : codec.write(mesh);
            Files.write(mgi, bytes);
            converted++;
            System.out.printf(Locale.ROOT, "converted=%s -> %s bytes=%d%n", obj, mgi, bytes.length);
        }

        System.out.printf(Locale.ROOT, "done converted=%d skipped=%d%n", converted, skipped);
    }

    private static Path mgiPathFor(Path sourceObj, boolean withMeshlets) {
        String file = sourceObj.getFileName().toString();
        int dot = file.lastIndexOf('.');
        String base = dot <= 0 ? file : file.substring(0, dot);
        return sourceObj.resolveSibling(base + (withMeshlets ? ".meshlets.mgi" : ".mgi"));
    }

    private static MgiStaticMesh toMgiWithMeshlets(MeshData mesh, int maxVerts, int maxTris) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null || indices.length == 0) {
            throw new IllegalArgumentException("Cannot generate meshlet metadata for non-indexed mesh");
        }
        List<Meshlet> meshlets = MeshletClusters.buildMeshlets(mesh, indices, maxVerts, maxTris);
        MgiMeshletData meshletData = toMgiMeshletData(mesh, meshlets);
        MgiStaticMesh base = MgiMeshDataCodec.toMgiStaticMesh(mesh);
        return new MgiStaticMesh(
            base.positions(),
            base.normalsOrNull(),
            base.uv0OrNull(),
            base.boundsOrNull(),
            base.canonicalMetadataOrNull(),
            meshletData,
            base.meshletLodDataOrNull(),
            base.indices(),
            base.submeshes()
        );
    }

    private static MgiMeshletData toMgiMeshletData(MeshData mesh, List<Meshlet> meshlets) {
        int[] indices = mesh.indicesOrNull();
        if (indices == null) {
            throw new IllegalArgumentException("mesh must be indexed");
        }
        List<Submesh> submeshes = mesh.submeshes();
        Map<Object, Integer> materialSlots = new HashMap<>();
        IntCollector remap = new IntCollector(4096);
        IntCollector triangles = new IntCollector(4096);
        ArrayList<MgiMeshletDescriptor> descriptors = new ArrayList<>(meshlets.size());
        ArrayList<MgiMeshletBounds> bounds = new ArrayList<>(meshlets.size());

        for (Meshlet meshlet : meshlets) {
            int first = meshlet.firstIndex();
            int count = meshlet.indexCount();
            if (first < 0 || count <= 0 || first + count > indices.length || (count % 3) != 0) {
                throw new IllegalArgumentException("invalid meshlet index range for export");
            }

            int triOffset = triangles.size() / 3;
            int remapOffset = remap.size();
            Map<Integer, Integer> localVertex = new HashMap<>();

            for (int i = first; i < first + count; i++) {
                int globalVertex = indices[i];
                Integer local = localVertex.get(globalVertex);
                if (local == null) {
                    local = localVertex.size();
                    localVertex.put(globalVertex, local);
                    remap.add(globalVertex);
                }
                triangles.add(local);
            }

            int submeshIndex = findSubmeshIndex(submeshes, first, count);
            int materialSlot = materialSlotFor(submeshes, submeshIndex, materialSlots);
            int boundsIndex = bounds.size();
            bounds.add(new MgiMeshletBounds(
                meshlet.bounds().minX(),
                meshlet.bounds().minY(),
                meshlet.bounds().minZ(),
                meshlet.bounds().maxX(),
                meshlet.bounds().maxY(),
                meshlet.bounds().maxZ()
            ));
            descriptors.add(new MgiMeshletDescriptor(
                submeshIndex,
                materialSlot,
                remapOffset,
                localVertex.size(),
                triOffset,
                count / 3,
                boundsIndex,
                0
            ));
        }

        return new MgiMeshletData(
            descriptors,
            remap.toArray(),
            triangles.toArray(),
            bounds
        );
    }

    private static int findSubmeshIndex(List<Submesh> submeshes, int firstIndex, int indexCount) {
        if (submeshes.isEmpty()) {
            return 0;
        }
        int end = firstIndex + indexCount;
        for (int i = 0; i < submeshes.size(); i++) {
            Submesh submesh = submeshes.get(i);
            int subFirst = submesh.firstIndex();
            int subEnd = subFirst + submesh.indexCount();
            if (firstIndex >= subFirst && end <= subEnd) {
                return i;
            }
        }
        for (int i = 0; i < submeshes.size(); i++) {
            Submesh submesh = submeshes.get(i);
            int subFirst = submesh.firstIndex();
            int subEnd = subFirst + submesh.indexCount();
            if (firstIndex < subEnd && end > subFirst) {
                return i;
            }
        }
        return 0;
    }

    private static int materialSlotFor(List<Submesh> submeshes, int submeshIndex, Map<Object, Integer> materialSlots) {
        if (submeshes.isEmpty()) {
            return 0;
        }
        Object materialId = submeshes.get(submeshIndex).materialId();
        if (materialId == null) {
            return 0;
        }
        if (materialId instanceof Number number) {
            int slot = number.intValue();
            if (slot < 0) {
                return 0;
            }
            return slot;
        }
        Integer existing = materialSlots.get(materialId);
        if (existing != null) {
            return existing;
        }
        int created = materialSlots.size() + 1;
        materialSlots.put(materialId, created);
        return created;
    }

    private static int parsePositive(String raw, String label) {
        int parsed = Integer.parseInt(raw);
        if (parsed <= 0) {
            throw new IllegalArgumentException(label + " must be > 0");
        }
        return parsed;
    }

    private static final class IntCollector {
        private int[] values;
        private int size;

        private IntCollector(int initialCapacity) {
            this.values = new int[Math.max(16, initialCapacity)];
        }

        private void add(int value) {
            if (size == values.length) {
                int[] grown = new int[values.length * 2];
                System.arraycopy(values, 0, grown, 0, values.length);
                values = grown;
            }
            values[size++] = value;
        }

        private int size() {
            return size;
        }

        private int[] toArray() {
            int[] out = new int[size];
            System.arraycopy(values, 0, out, 0, size);
            return out;
        }
    }
}
