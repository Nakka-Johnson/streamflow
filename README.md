# StreamFlow

> Cross-region Kafka streaming reference implementation in Java 17 + Spring Boot. Two-cluster topology, idempotent producer, manual-ack consumer with DLQ routing.

A working reference for the operational realities of multi-cluster Kafka:
why idempotent producers matter, how partition keys shape ordering
guarantees, where exactly-once breaks down, and how to recover when a
cluster fails.

---

## Status

This project is built in phases. The current state covers two-cluster
topology, idempotent producer, and consumer with manual offset commit
and dead-letter routing. Cross-cluster replication via MirrorMaker 2
and the failover runbook are tracked in [`docs/ROADMAP.md`](docs/ROADMAP.md).

---

## Architecture

The system implements a **passive-active multi-region topology** designed for regional fault tolerance and strict data consistency.

```mermaid
graph TD
    subgraph "Region: US-EAST (Primary)"
        P[Producer Service] -->|acks=all| PC[Primary Cluster]
        PC -->|Topic: events| C[Consumer Service]
        C -->|Manual Ack| PC
        C -->|Retry Exhausted| DLQ[Dead Letter Topic]
    end

    subgraph "Data Replication"
        PC -.->|MirrorMaker 2| SC[Secondary Cluster]
    end

    subgraph "Region: US-WEST (Secondary)"
        SC
        P2[Producer - standby]
    end

    style P fill:#f9f,stroke:#333,stroke-width:2px
    style C fill:#bbf,stroke:#333,stroke-width:2px
    style PC fill:#dfd,stroke:#333,stroke-width:4px
    style SC fill:#eee,stroke:#333,stroke-dasharray: 5 5
```


Design decisions
Why acks=all and enable.idempotence=true

acks=all means the leader waits for all in-sync replicas to acknowledge
before confirming the write. Slower than acks=1, but no data loss if
the leader fails before replication. enable.idempotence=true deduplicates
producer retries via per-partition sequence numbers, so a network blip
during retry will not produce duplicate messages within a session.

Trade-off: idempotence requires acks=all and constrains
max.in.flight.requests.per.connection to at most 5. Throughput drops
slightly versus fire-and-forget, but the correctness gain is worth it for
any system that cares about not double-counting events.

Why partition by tenant ID

All events for the same tenant land on the same partition, guaranteeing
in-order processing per tenant. Cross-tenant ordering is not preserved.
The risk is hot partitions if one tenant's traffic dominates. Per-partition
metrics expose the skew.

Why manual offset commit

Auto-commit can advance the offset before a consumer has finished
processing. If the consumer crashes between auto-commit and processing,
the message is silently lost. Manual commit means at-least-once delivery,
combined with idempotent downstream operations giving effectively
exactly-once semantics.

DLQ via DefaultErrorHandler

Retry with exponential backoff (1s, 2s, 4s), then route to dead-letter
topic if the retry budget is exhausted. Without DLQ routing, a poison
message can stall a partition indefinitely.

Running it locally
Prerequisites
Docker Desktop with 8 GB RAM allocated
Java 17 (java -version)
Maven 3.8+
Bring up the clusters
cd docker
docker compose up -d

Wait ~60 seconds for both clusters to elect controllers, then:

docker compose ps

All brokers should show running.

Create source topics
./scripts/create-topics.sh
Run the producer
cd producer
mvn spring-boot:run

Send a test event:

curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"tenant-001","type":"ORDER_CREATED","payload":{"orderId":"ord-1","amount":99.99}}'
Run the consumer

In a separate terminal:

cd consumer
mvn spring-boot:run

You should see the consumer log the event within seconds of publishing.

Repo layout
streamflow/
├── docker/
│   └── docker-compose.yml
├── producer/                   Spring Boot producer service
├── consumer/                   Spring Boot consumer with manual ack and DLQ
├── scripts/
│   └── create-topics.sh
└── docs/                       Architecture and roadmap (in progress)
What's next

See docs/ROADMAP.md
 for the planned phases:

MirrorMaker 2 cross-cluster replication
Failover simulation
Prometheus + Grafana observability
Testcontainers integration tests
References
https://kafka.apache.org/41/operations/geo-replication-cross-cluster-data-mirroring/
Kafka: The Definitive Guide, Chapter 8 (Cross-Cluster Data Mirroring)
https://cwiki.apache.org/confluence/display/KAFKA/KIP-382%3A+MirrorMaker+2.0
