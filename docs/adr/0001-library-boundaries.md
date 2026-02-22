# ADR 0001: Vectrix, MeshForge, DynamisLightEngine Boundaries

- Status: Accepted
- Date: 2026-02-22

## Context

The rendering stack uses three libraries with different abstraction levels:

- `vectrix`: math/kernel primitives
- `meshforge`: geometry processing and packing
- `dynamislightengine`: scene/render runtime

Without explicit boundaries, responsibilities drift and duplicated code appears (math reimplementation, mesh processing in engine code, renderer concerns in MeshForge).

## Decision

We enforce strict separation of concerns:

1. `vectrix` owns math primitives, SIMD/SoA kernels, packing helpers (half/snorm/octa), and low-level culling/math utilities.
2. `meshforge` owns mesh authoring model (`MeshData`), mesh processing ops, and immutable runtime geometry packaging (`PackedMesh`, meshlets, descriptors).
3. `dynamislightengine` owns scene graph, materials/lights, shader/pipeline binding, render passes, frame orchestration, and telemetry.

## Boundary Rules

### `vectrix` MUST NOT

- Depend on mesh topology, submeshes, materials, render passes, or backend APIs.

### `meshforge` MUST NOT

- Depend on render pipeline concepts (materials, light models, descriptor layouts, render graph, reflections/post).
- Call graphics APIs directly (`vk*`, OpenGL calls, swapchain/render pass logic).

### `dynamislightengine` MUST NOT

- Reimplement vector math, SIMD kernels, or low-level packing helpers already in `vectrix`.
- Reimplement mesh processing already in `meshforge` (weld, normals, tangents, cache/cluster/order, packing).

## Dependency Direction

Allowed:

- `vectrix` -> no dependency on MeshForge/engine
- `meshforge` -> may depend on `vectrix`
- `dynamislightengine` -> may depend on `meshforge` and `vectrix`

Disallowed:

- `meshforge` -> `dynamislightengine`
- `vectrix` -> `meshforge` or `dynamislightengine`

## Data Contracts

- `vectrix` input/output: scalars, vectors, matrices, SoA arrays, packed numeric formats.
- `meshforge` input: raw/imported mesh data; output: `PackedMesh`.
- `dynamislightengine` input: `PackedMesh` + scene/material/camera/light data; output: rendered frames + diagnostics.

## Consequences

Positive:

- Cleaner code ownership and review rules.
- Better testability and reuse.
- Lower integration friction when adding backends/features.

Tradeoff:

- Some conversions stay explicit at boundaries (by design).

## Review Checklist

Use this in PR reviews:

- Is math duplicated outside `vectrix`?
- Is mesh processing duplicated outside `meshforge`?
- Is rendering API/runtime policy introduced into `meshforge`?
- Is engine code consuming `PackedMesh` rather than raw ad hoc mesh arrays?

