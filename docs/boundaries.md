# MeshForge, Vectrix, DynamisLightEngine Boundaries

## Purpose

This document codifies ownership boundaries across:

- `vectrix`: math and low-level numeric primitives
- `meshforge`: geometry processing and packing
- `dynamislightengine`: rendering runtime and scene systems

These rules are enforced to keep MeshForge a pure mesh manipulation library and to prevent renderer-specific leakage into MeshForge core.

## Allowed / Disallowed by Library

### Vectrix

Allowed:

- Vector/matrix/quat/dual-quat math
- SoA/SIMD kernels
- Numeric packing helpers (Half, SNORM, Octa, etc.)
- Frustum/AABB/ray primitives

Disallowed:

- Mesh topology/indices/submeshes
- Materials/lights/scene graph
- Graphics API objects/calls

### MeshForge

Allowed:

- Mutable mesh authoring model (`MeshData`, schema, attributes)
- Processing ops (`Ops`) such as weld/normals/tangents/cache/cluster/order
- Immutable runtime geometry packaging (`PackedMesh`, meshlets, descriptor buffers)
- Loader-side format ingestion/decode (including meshopt decode in `meshforge-loader`)
- Dependency on Vectrix for math/packing helpers

Disallowed in `meshforge/src/main`:

- Graphics API symbols and imports (`org.lwjgl.*`, Vulkan/OpenGL bindings, shader compilers)
- Shader source/SPIR-V handling
- Descriptor/pipeline/render-pass/framebuffer setup
- Command recording/submission
- Material/light/post-processing logic
- Image readback/PNG output

### DynamisLightEngine

Allowed:

- Shader management and compilation
- Descriptor and pipeline setup
- Render pass/frame graph orchestration
- Buffer uploads and dispatch/draw calls
- Scene/material/light systems
- Backend integration (Vulkan/OpenGL/etc.)

Disallowed:

- Reimplementing math kernels from Vectrix
- Reimplementing mesh processing/packing already in MeshForge

## Dependency Direction

Allowed:

- `vectrix` -> none
- `meshforge` -> `vectrix`
- `dynamislightengine` -> `meshforge`, `vectrix`

Disallowed:

- `meshforge` -> `dynamislightengine`
- `vectrix` -> `meshforge` or `dynamislightengine`

## meshforge-demo Status

`meshforge-demo` is a smoke-test/integration harness and is explicitly non-contract.

- It may contain LWJGL/Vulkan/shaderc/offscreen render code.
- It is not part of MeshForge core API guarantees.
- Production systems must not depend on demo classes.

## Enforcement

- Run `scripts/ci_guardrails.sh` locally and in CI.
- Fail if forbidden imports appear in `meshforge/src/main`.
- Use PR review checklist:
  - Is math duplicated outside Vectrix?
  - Is mesh processing duplicated outside MeshForge?
  - Is renderer-specific logic introduced into MeshForge core?
