# MeshForge Architecture

Last updated: 2026-02-23

MeshForge has two representations by design:

- `MeshData`: flexible authoring and processing model
- `PackedMesh`: immutable runtime handoff model

## `MeshData` (authoring model)

Responsibilities:
- semantic attribute model keyed by `(AttributeSemantic, setIndex)`
- editable topology/index/submesh data
- operation-friendly storage and mutation
- optional derived caches (bounds, generated attributes)

`MeshData` is where importers and mesh ops work.

## Pipeline (`MeshOp`)

Pipeline shape:
- `MeshPipeline.run(mesh, op...)`
- each op can mutate in-place or return a transformed mesh

Current high-use ops:
- validate / remove degenerates
- weld / compact
- recalculate normals / tangents
- optimize vertex cache
- meshlet clustering and ordering
- bounds

Correctness note:
- Weld is boundary-safe. It intentionally does neighbor-cell checks to avoid missed merges across quantization cell boundaries.

## `PackedMesh` (runtime model)

Responsibilities:
- immutable buffer-backed geometry payload
- stable, explicit vertex/index layout contract
- safe cross-thread sharing and caching

Contains:
- vertex buffer views
- optional index buffer view
- layout metadata (`VertexLayout`)
- submesh ranges
- bounds

## Packing (`MeshPacker` + `PackSpec`)

`MeshPacker.pack(mesh, spec)` is the explicit authoring->runtime conversion boundary.

Current status:
- interleaved packing is implemented and production-used
- multi-stream packing is declared in API but not implemented
- packer validates required attributes/formats and fails fast for unsupported layout modes

## Dependency Direction

- `vectrix` -> none
- `meshforge` -> `vectrix`
- `dynamislightengine` (or other engines) -> `meshforge`, `vectrix`

Disallowed:
- renderer/runtime dependencies in `meshforge/src/main`

See `docs/boundaries.md` and `docs/adr/0001-library-boundaries.md`.

## Runtime Integration Boundary

Consumers should integrate against:
- `PackedMesh`
- `VertexLayout`
- `VertexBufferView` / `IndexBufferView`
- `Submesh`
- bounds

No renderer-specific API surface is required in MeshForge core.
