# MeshForge v1 Architecture

This split is intentional:
- `MeshData` is optimized for flexibility and clarity.
- `PackedMesh` is optimized for upload and rendering.

## Representation 1: MeshData (Authoring Model)

### MeshData responsibilities
`MeshData` represents a semantic, editable mesh form suitable for procedural generation, importers, mesh operations, and tooling.

It contains:
- `Topology` (TRIANGLES / LINES / POINTS in v1)
- optional index buffer (expected for triangles)
- `Submesh[]` draw ranges and material associations
- `VertexSchema` as the attribute contract
- attributes stored by semantic (`POSITION`, `NORMAL`, `UV0`, etc.)

### VertexSchema: the attribute contract
`VertexSchema` defines which attributes exist, their formats (for example `F32x3`, `UNORM8x4`), and set indices (`UV0`, `UV1`, `COLOR0`).

Schema drives correctness:
- ops can require or generate attributes
- packer can validate formats and convert/compress

### VertexAttributeView: stable access without exposing storage
`VertexAttributeView` provides:
- typed get/set operations
- bulk access where appropriate
- stable API even if storage changes later

This allows future implementation shifts (SoA/AoS, on-heap/off-heap) without breaking user code.

### Submesh: maps to draw calls
`Submesh` models draw ranges:
- `firstIndex`
- `indexCount`
- `materialId` (lightweight in v1)
- optional topology override (typically unnecessary in v1)

### Internal storage model (v1)
`MeshData` uses SoA-by-attribute internally (one primitive array per attribute, keyed by semantic and set index). This is intentional for operation throughput and clean packing boundaries.

Detailed implementation guidance lives in `docs/internal-storage.md`.

## Mesh Creation APIs

Both creation paths produce `MeshData`.

### MeshBuilder (ergonomic)
For small procedural shapes, debug geometry, tests, and examples.

Focus:
- fluent construction
- readability
- correctness checks

### MeshWriter (high-throughput)
For importers and large/performance-sensitive generation.

Focus:
- pre-allocation
- bulk attribute/index writes
- minimal per-element overhead

### Meshes (front door)
`org.meshforge.api.Meshes` provides convenience factories such as:
- `Meshes.builder(topology)`
- `Meshes.writer(schema, counts...)`
- curated primitives (`cube`, `sphere`, etc.)

## Mesh Processing Pipeline (Ops)

### MeshOp
`MeshData apply(MeshData in, MeshContext ctx)`

v1 rule: allow in-place mutation for performance, but document behavior clearly per op.

### MeshPipeline
`MeshPipeline.run(mesh, ops...)` applies operations sequentially.

### MeshContext
`MeshContext` provides shared scratch resources and settings:
- temp buffers/maps
- tolerance/epsilon config
- optional instrumentation hooks (kept minimal in v1)

### v1 operations
- validate/repair: schema checks, index range checks, optional degenerate cleanup
- generate: normals, tangents, bounds
- modify: weld/deduplicate, optional triangulate
- optimize: vertex cache reorder, optional compaction/reindex

Optional `Ops` façade exposes operation factories.

## Representation 2: PackedMesh (Runtime Model)

### PackedMesh responsibilities
`PackedMesh` is optimized for rendering:
- immutable
- layout-defined
- buffer-backed (`ByteBuffer`/`MemorySegment`)
- safe to share/cache across threads and renderers

It contains:
- one or more `VertexBufferView` streams
- optional `IndexBufferView`
- `VertexLayout` (bindings/offsets/stride)
- `Submesh[]` ranges
- computed `Boundsf` (AABB/Sphere)

### VertexLayout
Renderer-facing contract of semantics, formats, offsets, and per-buffer bindings/stride.

## Packing: MeshPacker and PackSpec

### PackSpec
Defines policy:
- interleaved vs multi-stream layout
- per-attribute compression
- index width policy (`u16`/`u32`/auto)
- alignment and stride rules

Literature-aligned realtime defaults and priority order are documented in `docs/performance-profile.md`.

### MeshPacker
`MeshPacker.pack(mesh, spec)` returns `PackedMesh`.

Packing may reorder geometry, compress formats, and choose index width by policy.

## Lifetime & Memory Model

v1 baseline:
- `MeshData`: GC-managed arrays/collections
- `PackedMesh`: direct `ByteBuffer` by default

Optional future path in `pack.memory`: segment/arena-backed allocations with explicit ownership; if exposed, define lifetime via `AutoCloseable`.

## Renderer Integration

MeshForge stays rendering-API agnostic.

Integration boundary:
- `VertexLayout`
- `VertexBufferView` / `IndexBufferView`
- `Submesh[]`
- `Boundsf`

Future API bridges should be optional (`org.meshforge.bridge.*`) and separate from core.

## Public API Surface (v1)

Common user-facing types:
- `MeshData`, `Submesh`
- `VertexSchema`, `AttributeSemantic`, `VertexFormat`, `VertexAttributeView`
- `MeshBuilder`, `MeshWriter`
- `MeshOp`, `MeshPipeline` (optional `Ops`)
- `PackSpec`, `MeshPacker`, `PackedMesh`, `VertexLayout`

Optional facades:
- `org.meshforge.api.Meshes`
- `org.meshforge.api.Ops`
- `org.meshforge.api.Packers`

## Typical Usage

```java
MeshData mesh = Meshes.builder(Topology.TRIANGLES)
    .schema(VertexSchema.standardLit())
    .add(...)
    .build();

mesh = MeshPipeline.run(mesh,
    Ops.weld(1e-6f),
    Ops.normals(60f),
    Ops.tangents(),
    Ops.optimizeVertexCache(),
    Ops.bounds()
);

PackedMesh packed = MeshPacker.pack(mesh, Packers.realtime());
```
