#!/usr/bin/env bash
set -euo pipefail

echo "==> Stopping MirrorMaker 2 process inside secondary-broker-1..."
docker exec secondary-broker-1 \
  pkill -f "connect-mirror-maker\|MirrorMaker" || echo "    (nothing to stop)"

echo "==> Done."
