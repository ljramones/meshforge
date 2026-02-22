# MeshForge Roadmap

This roadmap tracks planned features after the current v1 foundation.

## Active Now (Part 2: SoA + SIMD)
- [ ] Loader parse hot-path hardening (OBJ numeric/token parsing throughput).
- [ ] Add parse-focused fixture timing loops for fast A/B validation.
- [ ] Add SIMD-ready kernel boundary around normal/attribute packing and conversion.
- [ ] Track normalized parse throughput (`us / 1M verts`) on baseline fixtures.
- [ ] Keep parity tests green between legacy and fast OBJ loaders on all baseline fixtures.

## Near Term (v1 completion)
- Harden current ops with more fixture-based tests and benchmarks.
- Add JMH benchmarks for `OptimizeVertexCacheOp`, weld/compact, and packing throughput.
- Improve weld policy with configurable seam-safe keys (POSITION-only vs POSITION+UV+NORMAL).
- Add richer packer validation (format coverage, bounds/metadata consistency).
- Add lightweight OBJ/glTF import stubs to feed `MeshData` directly.
- Expand `meshforge-loader` format registry by porting parser logic from `DynamisFX-Importers`
  where feasible without JavaFX runtime coupling.

## v1.1 (engine-grade quality)
- Upgrade `RecalculateTangentsOp` to MikkTSpace-compatible output.
- Add candidate-queue tuning and optional overdraw optimization pass.
- Add `PackSpec.mobile()` profile tuned for tighter bandwidth budgets.
- Add optional octa normal packing path and shader decode reference notes.
- Add stronger policy controls for auto-generated vs fail-fast pack requirements.

## v1.2 (workflow and interoperability)
- Add `io.gltf.read` basic reader (positions/indices, then normals/uvs/material groups).
- Add `io.gltf.write` basic exporter for packed and authoring meshes.
- Add bridge examples for renderer upload descriptors from `VertexLayout`.
- Add migration helpers for external mesh sources (OBJ, tool exports).

## Later (advanced features)
- Multi-stream packing and stream partition policies.
- Meshlet/cluster generation path for modern GPU pipelines.
- Optional LOD generation pipeline integration.
- Skinning/morph-target authoring and packing extensions.
- Optional off-heap/arena-backed packed storage mode with explicit lifetime controls.

## Guiding Principles
- Keep `MeshData` flexible and processing-friendly.
- Keep `PackedMesh` immutable and renderer-ready.
- Prefer policy-driven behavior over hardcoded formats.
- Add complexity only when benchmark results justify it.
