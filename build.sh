#!/usr/bin/env bash
set -euo pipefail

echo "==> Running MeshForge release verification build"
mvn -Prelease clean verify

echo "==> Build complete"
