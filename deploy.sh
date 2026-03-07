#!/usr/bin/env bash
set -euo pipefail

echo "==> Running pre-deploy verification"
mvn -Prelease clean verify

echo "==> Deploying MeshForge to Sonatype Central"
mvn -Prelease deploy

echo "==> Deploy submitted"
echo "==> Review status in the Sonatype Central Portal"
