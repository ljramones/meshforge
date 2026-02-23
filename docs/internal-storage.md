# MeshForge Internal Storage

Last updated: 2026-02-23

## Canonical Representation

- `MeshData`: SoA-by-attribute (one primitive array per attribute)
- `PackedMesh`: packed runtime buffers produced by `MeshPacker`

This boundary is intentional:
- ops are easier and faster on semantic SoA data
- packing concerns stay isolated and explicit

## `MeshData` internals

`MeshData` owns:
- topology
- schema
- vertex count
- optional `int[]` indices
- submeshes
- attribute store
- optional morph target list

Authoring canonical formats:
- `POSITION` `F32x3`
- `NORMAL` `F32x3`
- `TANGENT` `F32x4`
- `UV*` `F32x2`
- `JOINTS` typically `I32x4`/`U16x4` at authoring boundaries
- `WEIGHTS` `F32x4`

## `PackedMesh` internals

Packed output currently:
- interleaved vertex stream
- optional index stream (`u16`/`u32` by policy)
- layout/offset/stride metadata

Implementation status:
- multi-stream mode is declared in `PackSpec`
- `MeshPacker` currently supports interleaved mode only

## Mutation and cache flags

Mesh operations are expected to invalidate derived data when needed:
- bounds
- generated normals/tangents (when topology/order changes)

## Design constraints

- no per-vertex object model in hot paths
- fail-fast validation at loader and pack boundaries
- deterministic output for identical inputs + options
