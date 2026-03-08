# Runtime Geometry Cache Format (Draft)

## Purpose

Define a MeshForge-native binary cache for runtime-ready geometry so cold loads can bypass text parsing and most prep work.

Current target flow:

```text
OBJ/other source -> realtime prep -> planned pack -> RuntimeGeometryPayload -> cache file
cache file -> RuntimeGeometryPayload -> GpuGeometryUploadPlan
```

## Scope (Phase E2 prototype)

The prototype caches `RuntimeGeometryPayload` fields needed by `meshforge-dynamisgpu`:

- vertex payload bytes
- index payload bytes
- vertex layout metadata
- vertex/index counts + index type
- submesh ranges

Not yet cached in prototype:

- bounds
- source provenance / hash
- pack preset fingerprint
- compression

## Binary Layout (v1 draft)

All values are little-endian.

Header:

- `magic` (`MFGC`, 4 bytes)
- `version` (`uint32`, currently `1`)

Core:

- `vertexCount` (`uint32`)
- `indexCount` (`uint32`)
- `indexTypeOrdinal` (`int32`, `-1` for non-indexed)
- `strideBytes` (`uint32`)

Vertex layout entries:

- `entryCount` (`uint32`)
- repeated entries:
  - `semanticOrdinal` (`int32`)
  - `setIndex` (`uint32`)
  - `vertexFormatOrdinal` (`int32`)
  - `offsetBytes` (`uint32`)

Payloads:

- `vertexByteCount` (`uint32`)
- `vertexBytes` (`byte[]`)
- `indexByteCount` (`uint32`)
- `indexBytes` (`byte[]`, empty if non-indexed)

Submeshes:

- `submeshCount` (`uint32`)
- repeated entries:
  - `firstIndex` (`uint32`)
  - `indexCount` (`uint32`)
  - `hasMaterialId` (`uint8`)
  - if `hasMaterialId == 1`: `materialIdUtf` (`utf`)

## Versioning

- Strict magic + version check on read.
- Any incompatible layout change increments version.
- Reader should fail fast for unknown versions.

## Next-step extensions

- add optional bounds block
- add source hash block (invalidates stale caches)
- add pack preset id / schema fingerprint
- add optional block compression
