# G1 Runtime Geometry Layout Strategy

## Goal

Select the long-term canonical runtime geometry layout contract across:

- MeshForge packing
- runtime cache format
- MeshForge -> DynamisGPU seam
- renderer upload/binding
- future meshlet/streaming work

## Options Evaluated

### Option A: Fully Interleaved (single vertex stream)

- One vertex buffer with packed attributes per vertex
- Current default behavior in runtime packing paths

### Option B: Multi-Stream (separate vertex streams)

- Split streams by attribute family (example: position, normal/tangent, uv/color/skin)
- Index buffer remains separate as usual

### Option C: Hybrid (PackSpec-driven, one canonical default)

- Keep one canonical runtime layout as default
- Permit specialized alternatives through explicit `PackSpec` modes later

## Decision Matrix

Scoring: `1` (weak) -> `5` (strong), based on current subsystem state and near-term priorities.

| Criterion | Interleaved | Multi-Stream | Hybrid |
|---|---:|---:|---:|
| Cache format stability | 5 | 2 | 4 |
| Seam simplicity (`RuntimeGeometryPayload -> GpuGeometryUploadPlan`) | 5 | 2 | 4 |
| GPU upload simplicity (today) | 5 | 3 | 4 |
| Vertex fetch efficiency (classic path) | 4 | 3 | 4 |
| Streaming/update flexibility | 2 | 5 | 4 |
| Memory/alignment control flexibility | 3 | 5 | 4 |
| Meshlet/cluster future compatibility | 4 | 4 | 5 |
| Implementation/maintenance complexity | 5 | 2 | 3 |
| **Total** | **33** | **26** | **32** |

## Recommendation

### Canonical runtime contract: Interleaved (Option A), with Hybrid extension path (Option C)

Rationale:

- Interleaved is the best fit for current architecture maturity and productionization goals.
- Cache and seam are already implemented and validated around this shape.
- It minimizes contract churn while Phase F cache/lifecycle hardening is still fresh.
- Hybrid keeps a clean future path for multi-stream without destabilizing core contracts now.

## Canonical Contract (Near-Term)

Canonical runtime payload remains:

- one interleaved vertex stream
- optional index stream
- layout metadata (`VertexLayout`)
- counts (`vertexCount`, `indexCount`)
- index type
- submesh ranges

This contract is persisted in runtime cache and consumed by `meshforge-dynamisgpu`.

## Consequences by Layer

### MeshForge packing

- Keep interleaved runtime output as default and primary optimized path.
- Continue using `PackSpec` as the future extension point for specialized layouts.

### Runtime cache

- Keep cache schema aligned to interleaved payload as canonical v1 runtime format.
- Avoid multi-stream cache schema complexity in current productionization phase.

### MeshForge -> DynamisGPU seam

- Keep translator and upload plan model simple: one vertex binding + optional index binding.
- Add extension points, but do not widen baseline contract yet.

### Renderer/GPU integration

- Stable binding model for immediate integration.
- Future multi-stream support should be additive, not disruptive.

## Required API/Data-Model Actions

### Now

- No breaking API changes required.
- Keep `RuntimeGeometryPayload` and `GpuGeometryUploadPlan` interleaved-centric by default.
- Preserve `PackSpec` as explicit mechanism for future layout variants.

### Later (additive)

- Introduce optional multi-stream payload contract variant.
- Extend bridge upload plan for N vertex streams.
- Version cache format when multi-stream cache payloads are introduced.

## Migration Impact

### Cache

- No immediate migration needed for current cache users.
- Future multi-stream introduction must be version-gated and reader-validated.

### Seam

- Current seam remains stable and cheap.
- Future stream expansion should be additive via new upload-plan fields/types.

## Future Extensions (Not in G1 implementation scope)

- meshlet/cluster-oriented payload sections
- chunked runtime geometry cache pages
- partial stream loading and residency-aware upload
- specialized layouts by mesh class (static world vs skinned assets)

## G1 Decision Statement

Canonical runtime geometry layout for Dynamis is **interleaved** for the current stable contract, with `PackSpec` reserved as the controlled extension point for future specialized/multi-stream layouts.
