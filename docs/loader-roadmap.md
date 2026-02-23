# Mesh Loader Status and Roadmap

Last updated: 2026-02-23

## Current Loader Registry

`MeshLoaders.defaults()` (production default):
- `obj` -> fast OBJ loader
- `stl`
- `ply`
- `off`

`MeshLoaders.defaultsLegacy()`:
- `obj` -> legacy line-based OBJ loader
- `stl`
- `ply`
- `off`

`MeshLoaders.planned()`:
- includes `gltf`/`glb` and a larger extension registry
- unsupported formats fail with explicit "not implemented yet" errors

## Implemented Formats

- OBJ (legacy + fast parser)
- STL (ASCII + binary)
- PLY (ASCII polygon triangulation)
- OFF (polygon triangulation)
- glTF / glb (loader implemented; available via planned registry or direct loader use)

## glTF Notes

- Geometry attributes supported
- Skinning attributes (`JOINTS_0` / `WEIGHTS_0`) supported
- Morph target ingestion supported
- Validation guardrails for accessor bounds/count/stride are in place

## Unsupported Planned Formats (explicit placeholders)

- DAE, X3D, 3MF, WRL
- USD/USDA/USDC/USDZ
- DXF, 3DS
- Maya ASCII (`.ma`)
- STEP/STP, IGES/IGS
- FXML

## Near-term Loader Work

- Expand glTF conformance coverage and fixtures
- Add format-specific parity tests between default and legacy OBJ where relevant
- Continue fail-fast validation for malformed/truncated files
