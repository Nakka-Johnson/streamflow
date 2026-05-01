#!/usr/bin/env bash
set -euo pipefail

PRIMARY="primary-broker-1:19092"

create_topic() {
    local name="$1"
    local partitions="$2"
    echo "==> Creating topic: ${name} (partitions=${partitions}, rf=3)"
    docker exec primary-broker-1 /opt/kafka/bin/kafka-topics.sh \
        --bootstrap-server "${PRIMARY}" \
        --create \
        --if-not-exists \
        --topic "${name}" \
        --partitions "${partitions}" \
        --replication-factor 3 \
        --config min.insync.replicas=2 \
        --config retention.ms=604800000
}

create_topic "events" 6
create_topic "transactions" 6
create_topic "audit" 3

echo ""
echo "==> Source topics on primary:"
docker exec primary-broker-1 /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${PRIMARY}" --list