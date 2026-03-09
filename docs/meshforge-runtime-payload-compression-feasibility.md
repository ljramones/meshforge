# MeshForge Runtime Payload Compression Feasibility

## Scope

This pass evaluates roadmap item #6 (compression) as a feasibility + foundation step.

It intentionally excludes:

- broad payload contract redesign
- mandatory compression in runtime export paths
- DynamisGPU-side decompression orchestration
- renderer policy integration

## Payloads Evaluated

The current runtime handoff payloads evaluated were:

- meshlet visibility bounds payload (`GpuMeshletVisibilityPayload`)
- meshlet LOD payload (`GpuMeshletLodPayload`)
- meshlet streaming payload (`GpuMeshletStreamingPayload`)

These are stable handoff payloads with explicit byte layouts and existing upload-prep seams.

## Feasibility Method

Baseline codec:

- JDK Deflate (`java.util.zip.Deflater` / `Inflater`), `BEST_SPEED`

Measurement path:

- serialize representative payload bytes from existing upload-prep paths
- compress/decompress in-process
- validate byte-exact round-trip
- record compressed size ratio and rough encode/decode timings

Representative results from `RuntimePayloadCompressionTest`:

- `visibility`: raw `480000` bytes -> compressed `154055` bytes, ratio `0.321`, encode `~2.3 ms`, decode `~1.0 ms`
- `lod`: raw `192` bytes -> compressed `114` bytes, ratio `0.594`, encode `~0.006 ms`, decode `~0.003 ms`
- `streaming`: raw `40960` bytes -> compressed `17567` bytes, ratio `0.429`, encode `~0.21 ms`, decode `~0.10 ms`

## Findings

1. Visibility and streaming payloads show strong compression potential (roughly 57–68% byte reduction in this feasibility run).
2. LOD payload also compresses, but absolute byte savings are small due to already-small payload size.
3. Decode cost exists and must be treated as an explicit tradeoff; compression should remain optional by payload class.
4. Compression viability is high enough to justify a small code foundation hook, but not mandatory-path rollout yet.

## Foundation Hook Added

Added a minimal, optional compression seam under MeshForge:

- `RuntimePayloadCompressionMode` (`NONE`, `DEFLATE`)
- `RuntimePayloadCompression` utility:
  - `compress(...)`
  - `decompress(..., expectedBytes, ...)`

This hook is intentionally narrow and not wired into all runtime export paths yet.

## Recommended Next Compression Step

1. Keep compression optional and selective (prioritize visibility and streaming payload classes first).
2. Add explicit per-payload metadata flags for compressed/uncompressed handoff where needed.
3. Validate end-to-end decode cost in realistic activation timings before enabling compression by default.
4. Defer broader codec exploration until optional-path behavior is validated against current runtime budgets.

## Not Covered Yet

- cross-module decompression orchestration policy in DynamisGPU
- MGI chunk-level compression strategy
- compression of large packed vertex/index payload blocks
- default-on compression policy

