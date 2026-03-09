# Meshlet Frustum Culling Results (Phase 1)

## Purpose
Validate a minimal CPU meshlet frustum-culling path using existing prebaked meshlet metadata, with no format/schema or renderer architecture changes.

## Scope
- CPU path only
- Uses prebaked meshlet bounds directly from MGI meshlet metadata
- No cone culling, no hierarchy/LOD, no GPU-driven visibility

## Benchmark Command

```bash
mvn -q -f meshforge-demo/pom.xml exec:java \
  -Dexec.mainClass=org.dynamisengine.meshforge.demo.MeshletFrustumCullingFixtureTiming \
  -Dexec.args="--warmup=2 --runs=9"
```

## Frustum Scenario
- Scenario: `centered-axis-aligned-window`
- Window ratio: `0.50`
- Bounds source: prebaked meshlet bounds (`usesPrebakedBounds=true`)

## Results

| Fixture | Total Meshlets | Visible Meshlets | Total Triangles | Visible Triangles | Triangle Reduction | Culling Median ms | Culling p95 ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| `RevitHouse.obj` | 19718 | 10875 | 414060 | 228357 | 44.85% | 0.238 | 0.905 |
| `xyzrgb_dragon.obj` | 5762 | 2842 | 249882 | 125211 | 49.89% | 0.093 | 0.100 |

## Interpretation
- Visible triangle reduction is substantial on both fixtures (~45-50%).
- Culling overhead is small and stable (sub-millisecond median on both fixtures).
- No additional MGI schema work was required.

## Phase 1 Verdict
Phase 1 is successful:
- measurable visibility reduction
- low culling overhead
- no format changes
- clear foundation for Phase 2 (GPU-driven cluster visibility)
