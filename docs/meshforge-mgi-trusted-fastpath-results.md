# MeshForge MGI Trusted Fast Path Results

Date: 2026-03-09

## Summary
The trusted MGI fast path is validated.

Measured outcome:
- trusted path usage is observed (`Trusted Fast Used = yes`)
- runtime topology stages are removed for trusted assets:
  - `validate`
  - `removeDegenerates`
  - `bounds`
- end-to-end TTFU improves materially, strongest on large meshes

## Benchmark Command
```bash
mvn -q -f meshforge-demo/pom.xml exec:java \
  -Dexec.mainClass=org.dynamisengine.meshforge.demo.PrepQueueTransferTtfuFixtureTiming \
  -Dexec.args="--mode=all --max-inflight=2 --warmup=2 --runs=9"
```

## Median Comparison (Task 5)

| Fixture | Mode | Trusted Fast Used | Pipeline ms | Pipeline Topology ms | Total TTFU ms |
|---|---|---|---:|---:|---:|
| `xyzrgb_dragon.obj` | `mgi-full` | no | 1.908 | 0.855 | 5.961 |
| `xyzrgb_dragon.obj` | `mgi-trusted-fast` | yes | 0.003 | 0.000 | 4.444 |
| `lucy.obj` | `mgi-full` | no | 0.720 | 0.332 | 2.647 |
| `lucy.obj` | `mgi-trusted-fast` | yes | 0.002 | 0.000 | 1.996 |
| `RevitHouse.obj` | `mgi-full` | no | 7.115 | 3.640 | 18.592 |
| `RevitHouse.obj` | `mgi-trusted-fast` | yes | 0.003 | 0.000 | 12.630 |

## Copy Floor Anchor (Task 6)
Task 6 measured payload copy floor using trusted-preprocessed payloads:

| Fixture | Total Payload | Total Copy+Setup ms | Effective GB/s |
|---|---:|---:|---:|
| `RevitHouse.obj` | 24,820,308 B | 0.402 | 61.672 |
| `xyzrgb_dragon.obj` | 4,999,640 B | 0.064 | 78.119 |
| `lucy.obj` | 1,399,612 B | 0.027 | 52.732 |

Interpretation:
- remaining trusted-fast TTFU gap is not raw memory-copy bandwidth
- remaining costs are activation/packing/control-path overhead

## Architectural Conclusion
Current geometry activation model is now:

```text
source -> loader -> MGI -> trusted runtime fast path -> minimal prep -> payload copy -> UploadManager -> GPU
```

This closes the two highest taxes previously measured:
1. source-ingest parse tax (MGI)
2. runtime topology canonicalization tax (trusted fast path)

## Operational Policy
- trusted fast path should be preferred for trusted-canonical MGI assets
- safe fallback remains required for:
  - older files
  - missing metadata/bounds
  - trust disabled mode

## Next Direction
With topology tax and copy floor characterized, future gains are expected from representation-level work:
- optional packed runtime payload chunk (see `docs/meshforge-mgi-packed-payload-design-note.md`)
- meshlet metadata/storage path (import-time generation, runtime consumption)
