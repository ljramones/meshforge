#!/usr/bin/env bash
set -euo pipefail

PROJECT_VERSION="$(mvn -q -DforceStdout help:evaluate -Dexpression=project.version | tail -n1)"
if [[ "$PROJECT_VERSION" == *-SNAPSHOT ]]; then
  echo "Refusing deploy: project.version is SNAPSHOT ($PROJECT_VERSION)"
  exit 1
fi

echo "==> Release version: $PROJECT_VERSION"
echo "==> Running pre-deploy verification"
mvn -Prelease clean verify

echo "==> Deploying MeshForge to Sonatype Central"
mvn -Prelease deploy

echo "==> Deploy submitted"
echo "==> Review status in the Sonatype Central Portal"
