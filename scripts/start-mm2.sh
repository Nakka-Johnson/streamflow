#!/usr/bin/env bash
set -euo pipefail

# Starts MirrorMaker 2 in standalone mode inside the secondary cluster.
# MM2 is co-located with the TARGET cluster (it consumes remotely from
# source, produces locally to target), which is best practice.

cd "$(dirname "$0")/.."

echo "==> Copying MM2 config into secondary-broker-1..."
docker cp connectors/mm2.properties secondary-broker-1:/tmp/mm2.properties

echo "==> Starting MirrorMaker 2 standalone..."
docker exec -d secondary-broker-1 \
  /opt/kafka/bin/connect-mirror-maker.sh \
  /tmp/mm2.properties

echo "==> Waiting 30s for MM2 to initialise..."
sleep 30

echo "==> Verifying MM2 process is running..."
if docker exec secondary-broker-1 ps aux | grep -v grep | grep -q "connect-mirror-maker\|MirrorMaker"; then
    echo "    MM2 process is running"
else
    echo "    MM2 process NOT found, check logs:"
    echo "    docker logs secondary-broker-1 | tail -50"
    exit 1
fi

echo ""
echo "==> Topics on secondary cluster:"
docker exec secondary-broker-1 /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server secondary-broker-1:19092 --list

echo ""
echo "==> Done. Replicated topics will appear with 'us-east.' prefix."
