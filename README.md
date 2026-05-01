# StreamFlow

> Cross-region Kafka streaming reference implementation in Java 17 + Spring Boot.  
> Two-cluster topology, idempotent producer, manual-ack consumer with DLQ routing.

A working reference for the operational realities of multi-cluster Kafka:  
why idempotent producers matter, how partition keys shape ordering guarantees,  
where exactly-once breaks down, and how to recover when a cluster fails.

---

## Status

This project is built in phases. The current state covers:

- Two-cluster topology  
- Idempotent producer  
- Consumer with manual offset commit  
- Dead-letter queue (DLQ) routing  

Cross-cluster replication via MirrorMaker 2 and failover runbook are tracked in [`docs/ROADMAP.md`](docs/ROADMAP.md).

---

## Architecture

The system implements a **passive-active multi-region topology** designed for regional fault tolerance and strong data consistency.

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
