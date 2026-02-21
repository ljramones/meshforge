# Mesh Loader Roadmap

This roadmap defines the plan for reading multiple file types into `MeshData` through `meshforge-loader`.

## Loader API Shape

Use format-specific factory methods in `MeshLoaderFactory` and registry dispatch via `MeshLoaders`.

- Factory methods (one per format):
  - `obj()`, `stl()`, `ply()`, `gltf()`, `glb()`, `dae()`, `x3d()`, `off()`, `threemf()`, `wrl()`, `usd()`, `dxf()`, `tds()`, `mayaAscii()`, `cadStep()`, `cadIges()`, `fxml()`
- `MeshLoaders.defaults()`:
  - stable production-safe set (currently OBJ, STL, PLY, OFF)
- `MeshLoaders.planned()`:
  - all known extensions registered, unimplemented formats fail with explicit errors

## Integration Source

Source parser logic can be adapted from:
`/Users/larrymitchell/tripsnew/DynamisFX/DynamisFX-Importers`

Important constraint:
- DynamisFX importers target JavaFX/DynamisFX `Model3D` and cannot be used directly as-is.
- MeshForge loaders must output `MeshData` and stay renderer/UI agnostic.

## Implementation Phases

1. Foundation
- Keep OBJ loader stable
- Keep registry/factory API stable
- Add fixture-based tests per format port

Implemented now:
- OBJ (minimal positions + indexed triangles)
- STL (ASCII, triangle facets)
- PLY (ASCII, polygon triangulation)
- OFF (polygon triangulation)

2. Low-risk text formats
- OFF
- PLY
- STL (ASCII first, binary second)
- WRL (subset)

3. Structured scene formats
- glTF/GLB (geometry path first)
- DAE
- X3D

4. Advanced/industrial formats
- USD/USDA/USDC/USDZ
- DXF
- 3DS
- Maya ASCII (`.ma`)
- CAD (STEP/IGES)

## Per-format Acceptance Criteria

For each format:
- Loads positions and triangle indices into `MeshData`
- Produces deterministic index ordering
- Sets one submesh range at minimum
- Passes `Ops.validate()`
- Works through `MeshLoaders.planned().load(path)` with extension dispatch

## Policy

- Parse generously, fail with actionable messages.
- Keep authoring representation canonical (`POSITION F32x3`, indexed triangles).
- Add normals/uv/tangents only when source data is present and valid.
