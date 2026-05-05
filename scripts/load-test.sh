#!/usr/bin/env bash
set -euo pipefail

# Usage: ./scripts/load-test.sh [total_events] [events_per_sec]
# Generates synthetic events across multiple tenants to exercise
# partitioning, throughput, and consumer lag dynamics.

TOTAL="${1:-500}"
RATE="${2:-50}"
DEFAULT_HOST="localhost"
if [[ -r /proc/version ]] && grep -qi microsoft /proc/version; then
    DEFAULT_HOST="host.docker.internal"
fi

ENDPOINT="${ENDPOINT:-http://${DEFAULT_HOST}:8080/events}"

TENANTS=("tenant-001" "tenant-042" "tenant-099" "tenant-128" "tenant-256" "tenant-512" "tenant-777")

echo "==> Sending ${TOTAL} events at ${RATE}/sec to ${ENDPOINT}"

DELAY=$(awk "BEGIN {printf \"%.4f\", 1/${RATE}}")
START=$(date +%s)

for ((i=1; i<=TOTAL; i++)); do
    TENANT="${TENANTS[$((RANDOM % ${#TENANTS[@]}))]}"
    PAYLOAD="{\"orderId\":\"ord-${i}\",\"amount\":$((RANDOM % 10000))}"

    curl -s -X POST "${ENDPOINT}" \
        -H "Content-Type: application/json" \
        -d "{\"tenantId\":\"${TENANT}\",\"type\":\"ORDER_CREATED\",\"payload\":${PAYLOAD}}" \
        > /dev/null

    if (( i % 100 == 0 )); then
        ELAPSED=$(($(date +%s) - START))
        echo "  sent ${i}/${TOTAL} (${ELAPSED}s elapsed)"
    fi

    sleep "${DELAY}"
done

ELAPSED=$(($(date +%s) - START))
echo "==> Done. ${TOTAL} events in ${ELAPSED}s"
