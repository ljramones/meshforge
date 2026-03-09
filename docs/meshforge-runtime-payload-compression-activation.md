# MeshForge Runtime Payload Compression Activation

## Scope

This pass activates optional compression selectively for high-value payload classes only.

Activated:

- meshlet visibility payload
- meshlet streaming payload

Not activated:

- meshlet LOD payload (low absolute byte savings)

## Activation Design

Compression remains opt-in and explicit.

A small shared contract was added:

- `CompressedRuntimePayload`
  - `mode`
  - `uncompressedByteSize`
  - `payloadBytes`
  - deterministic round-trip restore via `toUncompressedBytes()`

Visibility payload API:

- `GpuMeshletVisibilityPayload.toCompressedBoundsPayload(RuntimePayloadCompressionMode mode)`

Streaming payload API:

- `GpuMeshletStreamingPayload.toCompressedUnitsPayload(RuntimePayloadCompressionMode mode)`

Supported modes are unchanged:

- `NONE`
- `DEFLATE`

## Why Visibility + Streaming

From the feasibility pass:

- visibility: strong compression candidate
- streaming: worthwhile candidate
- LOD: compressible but too small to justify added path complexity in this slice

## Validation

Added round-trip and semantics tests for activated payloads:

- deflate path restores canonical bytes exactly
- none path matches existing uncompressed bytes exactly
- compressed payload metadata (`mode`, `uncompressedByteSize`) remains consistent

Added contract-level mismatch validation:

- size mismatch on decompression is rejected

## Deferred

- default-on compression policy
- broad compression rollout to all payloads
- DynamisGPU-side compressed ingestion/decompression wiring
- alternate codec rollout

## Next Required DynamisGPU Work

To consume activated compressed payloads end-to-end, DynamisGPU needs:

1. optional compressed payload ingestion contract for visibility + streaming resources
2. deterministic decompression step at ingestion/resource boundary
3. mode-aware validation and fallback to uncompressed path

This keeps the rollout narrow and reversible while enabling targeted bandwidth savings where they matter most.

