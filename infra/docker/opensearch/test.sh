#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../../.." && pwd)"
compose_file="$repo_root/infra/docker/opensearch/compose.test.yml"
cleanup() { docker compose -f "$compose_file" down --volumes; }
trap cleanup EXIT
docker compose -f "$compose_file" up -d --wait --wait-timeout 180
cd "$repo_root/apps"
./gradlew :knowledge:openSearchIntegrationTest
