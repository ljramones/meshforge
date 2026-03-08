# Runtime Geometry Cache Lifecycle Policy

## Goal

Define stable placement, validation, and rebuild behavior for `.mfgc` runtime geometry caches.

## Placement Policy

Default cache location is a sidecar file next to the source asset:

```text
models/lucy.obj
models/lucy.mfgc
```

Policy helper:

- `RuntimeGeometryCachePolicy.sidecarPathFor(sourceAsset)`

Optional override:

- tools/benchmarks may pass an explicit cache directory.

## Validation Rules

Cache is accepted only when all checks pass:

- magic matches (`MFGC`)
- version matches supported reader version
- endianness is supported
- flags are supported and consistent with payload
- layout hash matches decoded layout metadata
- payload is complete (not truncated/corrupt)

Validation implementation:

- `RuntimeGeometryCacheIO.read(...)`

## Rebuild Triggers

Rebuild when any condition is true:

- forced rebuild requested
- cache file missing
- source asset timestamp is newer than cache
- cache validation fails (header/flags/hash/corruption mismatch)

Policy helper:

- `RuntimeGeometryCachePolicy.shouldRebuild(source, cache, forceRebuild)`

## Lifecycle Flow

```text
load(asset):
    cache = sidecarPathFor(asset)
    if shouldRebuild(asset, cache, forceRebuild):
        rebuild cache from source import path
    else:
        try read cache
        if read fails:
            rebuild cache from source import path
```

Current integration utility:

- `RuntimeGeometryCacheLifecycle.loadOrBuild(...)`

## Writing Policy (Current)

- cache write is synchronous after successful source import + runtime prep + planned pack
- no background or offline cache build is required in the current baseline

## Future Extensions

- cache placement variants (hashed cache roots)
- async/background cache generation
- offline bake pipeline integration
- stale cache cleanup policy
