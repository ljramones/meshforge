# MeshForge Technology Explainer

This guide explains what MeshForge is, what design decisions it makes, and how to integrate it cleanly in an engine or content pipeline.

## What MeshForge Is

MeshForge is a Java mesh-processing library that turns editable mesh data into validated, optimized, packed runtime buffers. It is renderer-agnostic and intentionally separated from graphics API concerns.

MeshForge sits between import/generation and runtime rendering:

1. Load or generate `MeshData`
2. Run deterministic operations
3. Pack to immutable `PackedMesh`

## Design Decisions

### 1. Authoring and runtime data are different on purpose

`MeshData` is flexible and mutable for import and processing. `PackedMesh` is immutable and compact for handoff to runtime systems.

### 2. Schema-first attribute contracts

`VertexSchema` declares exactly which attributes exist and how they are encoded. Operations and packers rely on schema contracts instead of implicit assumptions.

### 3. Correctness and fail-fast boundaries

MeshForge validates loader inputs and operation preconditions early, rejecting malformed or overflow-prone data before expensive work begins.

### 4. Submesh-safe topology mutation

Index-mutating operations preserve material/submesh partition semantics by operating in submesh-aware ranges instead of flattening everything into one global range.

### 5. Asset-time optimization focus

Operations such as weld, cache reorder, and meshlet clustering target import/build-time throughput and output quality, not per-frame runtime execution.

## Core Capability Areas

### Editable mesh model

- Topology, indices, submeshes
- Schema-backed attributes
- Morph target delta streams
- Optional cached bounds

### Processing pipeline

- Validation and guardrails
- Degenerate removal and weld
- Normal/tangent generation
- Vertex/index optimization
- Meshlet clustering and ordering

### Packing

- Interleaved packed vertex/index buffers
- Attribute format conversion
- Index width policy (`uint16`/`uint32`)
- Optional meshlet descriptor generation

## Loader Support

- OBJ and FastOBJ
- STL
- PLY
- OFF
- glTF/glb (including skinning attributes and morph target ingestion)

## Known v1 Scope Boundaries

- `PackSpec.LayoutMode.MULTI_STREAM` is declared but not yet implemented in the packer output path.
- Morph targets are represented in `MeshData` and loaded from glTF; v1 packed output is base-mesh only.
- Mesh decimation and UV unwrap are roadmap items, not v1 core.

## Integration Guidance

### Recommended import path

1. Load via `meshforge-loader`
2. Run `Pipelines.realtime(...)` or `Pipelines.realtimeFast(...)`
3. Pack with `Packers.realtime(...)`
4. Persist packed data in your own runtime asset format

### Recommended testing stance

- Keep STRICT loader/validation behavior in CI
- Re-run stress fixtures on parser and op changes
- Track benchmark deltas for weld/pack/cache paths before release

## Where to Start

- Public API: `API.md`
- Getting started: `README.md`
- Architecture details: `docs/meshforge-architecture.md`
- Stress and guardrails: `docs/stress-guarantees.md`
