# Mesh Fixtures

Use this guide to source local test assets for `meshforge-loader` and `meshforge-demo`.

## Recommended sources

- OFF: ModelNet (Princeton) - https://modelnet.cs.princeton.edu/download.html
- PLY: Stanford 3D Scanning Repository - https://graphics.stanford.edu/data/3Dscanrep/
- STL: Thingi10K - https://github.com/Thingi10K/Thingi10K
- OBJ: ShapeNet (approval required) - https://shapenet.org/
- GLTF/GLB (future loader): Khronos Sample Assets - https://github.khronos.org/glTF-Assets/

## Local fixture layout

Store assets in this repository under `fixtures/`:

- `fixtures/obj/small`, `fixtures/obj/medium`, `fixtures/obj/broken`
- `fixtures/stl/small`, `fixtures/stl/medium`, `fixtures/stl/broken`
- `fixtures/ply/small`, `fixtures/ply/medium`, `fixtures/ply/broken`
- `fixtures/off/small`, `fixtures/off/medium`, `fixtures/off/broken`
- `fixtures/gltf/small`, `fixtures/gltf/medium`, `fixtures/gltf/broken`

Current repo state:
- Populated now: `fixtures/obj/medium`, `fixtures/stl/medium`, `fixtures/gltf/medium`
- Present but currently empty: `fixtures/off`, `fixtures/ply`

Naming convention:
- Valid: `cube-valid.obj`, `bunny-medium.ply`
- Broken parser case: `missing-face-index.broken.obj`

## How to use quickly

First, install local meshforge snapshots used by the demo:

```bash
mvn -pl meshforge,meshforge-loader -DskipTests install
```

Demo UI:

```bash
mvn -pl meshforge-demo javafx:run
```

CLI demo:

```bash
mvn -pl meshforge-demo -Dexec.mainClass=org.meshforge.demo.MeshForgeDemo -Dexec.args="fixtures/obj/medium/suzanne.obj" exec:java
```

Loader tests should keep tiny inline fixtures for deterministic unit coverage and use `fixtures/` for integration samples.
