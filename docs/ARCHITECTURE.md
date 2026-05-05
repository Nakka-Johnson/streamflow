# Architecture

This document explains the design decisions behind StreamFlow at a level deeper than the README, with the trade-offs spelled out.

## System goals

StreamFlow is a reference implementation, not a product. It aims to demonstrate, end to end, the operational reality of running a Kafka topology that spans more than one cluster. Specifically:

1. Producers must not lose data, even when individual brokers fail
2. Consumers must not silently drop messages, even when they crash mid-batch
3. A primary cluster outage must be recoverable on the secondary cluster
4. Every operational concern (lag, throughput, latency) must be observable, not inferred

## Producer design

### Why idempotent producer

Without `enable.idempotence=true`, a Kafka producer that retries a send after a network blip can write the same message twice. The broker has no way to know that the second attempt is a retry rather than a new message. Idempotent producers attach a producer ID and a per-partition sequence number to every send, and the broker rejects duplicates on the same `(producerId, partition, sequence)` tuple.

The cost is mild: idempotence requires `acks=all` and constrains `max.in.flight.requests.per.connection` to at most 5. Throughput drops by a few percent compared to fire-and-forget. The correctness gain is worth it for any system whose downstream cares about not double-counting events.

### Why partition by tenant ID

Kafka guarantees order within a partition, not across partitions. By keying on tenant ID, all events for a given tenant land on the same partition and process in order. Cross-tenant ordering is not preserved, but the system does not need it.

The risk is hot partitions. If `tenant-001` produces 100x the traffic of every other tenant, the partition serving `tenant-001` will become a bottleneck. StreamFlow exposes per-partition metrics so this skew is observable.

### Why `acks=all` instead of `acks=1`

`acks=1` means the producer is acknowledged once the leader has written the message to its log, before any followers have replicated it. If the leader crashes immediately after, the message is lost.

`acks=all` waits for all in-sync replicas to acknowledge. Combined with `min.insync.replicas=2` on the topic, this guarantees that any acknowledged message has been durably written to at least two brokers.

## Consumer design

### Manual offset commit

The consumer commits offsets only after successful processing. This is the difference between at-least-once and at-most-once delivery semantics.

With auto-commit, the consumer commits on a fixed interval regardless of whether the work has been done. If the consumer crashes between the auto-commit and the actual processing, the message is silently lost. Manual commit eliminates this.

The trade-off is duplicates. If the consumer crashes after processing but before committing, the message will be redelivered on restart. Downstream operations must therefore be idempotent.

### Polling and session tuning

Three settings interact:

- `max.poll.records` (default 500) caps how many records `poll()` returns per call
- `max.poll.interval.ms` (default 300_000) is the upper bound on time between polls before the consumer is kicked from the group
- `session.timeout.ms` is how long the broker waits without a heartbeat before declaring the consumer dead

If processing is slow and `max.poll.records` is high, the consumer can spend more than `max.poll.interval.ms` working through one batch and get evicted. The fix is either lower `max.poll.records` or raise `max.poll.interval.ms`.

### Dead-letter routing

`DefaultErrorHandler` retries with exponential backoff (1s, 2s, 4s) and then routes to a dead-letter topic if the retry budget is exhausted. Without DLQ routing, a single bad message can stall consumption indefinitely.

## MirrorMaker 2

### What it actually replicates

MM2 has three connectors:

1. `MirrorSourceConnector` replicates topic data, headers, keys, values, and timestamps.
2. `MirrorCheckpointConnector` writes consumer group offset translations into a topic on the target cluster. Necessary because partition log positions on source and target do not align.
3. `MirrorHeartbeatConnector` emits heartbeat messages on both clusters. Even when no business traffic is flowing, the heartbeat lets monitoring verify replication is alive.

### The naming convention

By default, MM2 prefixes replicated topics with the source cluster alias. A topic called `events` on the cluster aliased `us-east` becomes `us-east.events` on the target.

This serves two purposes:

1. It makes the data lineage obvious. Reading `us-east.events` on the secondary cluster, you know exactly where the data came from.
2. It prevents replication loops in active-active topologies.

### Limits to be honest about

- **No exactly-once across clusters.** EOS works inside a single cluster via transactions. MM2 replication is at-least-once. Expect duplicates after failover.
- **Replication lag is real and unavoidable.** Network RTT plus MM2 polling intervals mean the secondary trails the primary by seconds to minutes. RPO is non-zero.
- **Offset translation is approximate.** MM2 writes checkpoints periodically. Failover between checkpoints means some replay.

## What this does not address

To keep StreamFlow useful as a learning artifact, several real production concerns are out of scope:

- **Authentication and authorization.** Production clusters use SASL/SCRAM or mTLS plus ACLs.
- **Schema management.** A real system would use Schema Registry with Avro to handle schema evolution.
- **Encryption.** TLS for in-transit, encryption at rest for the broker disks.
- **Multi-region quotas and rate limits.** Bandwidth between regions costs money. Production setups carefully budget MM2's replication traffic.
- **Automated chaos testing.** A real system would routinely kill brokers, partition the network, and fill disks to verify recovery paths still work.

