# MeshForge MGI Meshlet Benchmark Results

## Purpose
Compare runtime meshlet generation against loading prebaked meshlet metadata from MGI sidecars.

This validates whether meshlets should be treated as import-time/prebaked metadata rather than generated routinely during runtime activation.

## Command

```bash
mvn -q -f meshforge-demo/pom.xml exec:java \
  -Dexec.mainClass=org.dynamisengine.meshforge.demo.MeshletMgiLoadVsRuntimeTiming \
  -Dexec.args="--warmup=2 --runs=9"
```

## Results (median/p95)

| Fixture | Mode | Meshlets | Time ms (median) | Time ms (p95) | Whole Visible Tris | Meshlet Visible Tris | Triangle Reduction |
|---|---|---:|---:|---:|---:|---:|---:|
| `RevitHouse.obj` | `runtime-generate` | 19718 | 20.383 | 21.613 | 414060 | 228357 | 44.85% |
| `RevitHouse.obj` | `mgi-prebaked-load` | 19718 | 8.774 | 9.835 | 414060 | 228357 | 44.85% |
| `xyzrgb_dragon.obj` | `runtime-generate` | 5762 | 19.191 | 20.906 | 249882 | 125211 | 49.89% |
| `xyzrgb_dragon.obj` | `mgi-prebaked-load` | 5762 | 3.041 | 3.366 | 249882 | 125211 | 49.89% |

## Interpretation

- Prebaked meshlet metadata preserves the same culling signal as runtime generation (same meshlet counts and visible-triangle reduction in this run).
- Load-time decode is substantially cheaper than runtime generation:
  - `RevitHouse`: ~2.3x faster (`20.383 ms` -> `8.774 ms`)
  - `dragon`: ~6.3x faster (`19.191 ms` -> `3.041 ms`)

## Architectural Conclusion

Meshlets should be treated as a prebaked asset capability in MGI rather than generated on routine runtime activation paths for complex assets.

Recommended policy:
- generate meshlets at import/export time
- store in optional MGI meshlet chunks
- load and validate at runtime
- reserve runtime generation for tooling/debug/experimentation only
