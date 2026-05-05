# Failover Runbook

What to do when the primary Kafka cluster goes down.

## Detection

The system declares the primary cluster down when:

1. All three brokers fail health checks for more than 60 seconds, OR
2. The producer's `streamflow.events.failed` counter exceeds 10% of total publishes for more than 2 minutes, OR
3. MM2's heartbeat from primary stops appearing on secondary for more than 90 seconds

Any of these triggers a page. False positives from network blips are filtered by the time thresholds.

## Decision tree

| Condition | Action |
|---|---|
| Single broker failure, ISR still >= 2 | No failover. Restart the failed broker. The cluster is still write-available. |
| Two broker failures simultaneously | Investigate quickly. If ISR drops below `min.insync.replicas`, writes will start failing with `NotEnoughReplicasException`. Consider failover only if recovery looks longer than 30 minutes. |
| Full primary cluster outage | Failover to secondary. |
| Primary network partition (brokers up but unreachable) | Treat as full outage from external perspective. Failover. |

## Failover procedure

1. **Confirm primary is unrecoverable in the next 15 minutes.** Failover is disruptive; do not trigger it for transient issues.

2. **Stop all producers.** New writes during failover create split-brain risk. Producers will queue locally up to `buffer.memory` (32 MB default in StreamFlow), then start dropping. This is intentional.

3. **Verify MM2 has caught up.** Check the lag on `us-east.events` on the secondary cluster. If MM2 itself is lagging by more than 60 seconds, wait. Failing over to a stale secondary loses data.

4. **Switch consumer configuration.** Update `consumer/src/main/resources/application.yml`:
```yaml
   streamflow:
     consumer:
       bootstrap-servers: localhost:9094,localhost:9194,localhost:9294
       topic: us-east.events       # MM2-prefixed topic name
```
   Restart consumer. It will resume from the translated offsets (subject to MM2 checkpoint interval).

5. **Switch producer configuration.** Producer needs to write to the secondary, which now becomes the primary:
```yaml
   spring:
     kafka:
       bootstrap-servers: localhost:9094,localhost:9194,localhost:9294
```
   Restart producer.

6. **Verify end-to-end flow.** Send a test event and confirm it appears in consumer logs within seconds.

## Expected loss

For a clean failover triggered before the primary fully dies:

- **Producer-side:** zero data loss if producers stop writing before failover.
- **Consumer-side:** up to one MM2 checkpoint interval of redelivery (~10 seconds in StreamFlow's config). Idempotent downstream operations absorb this.

For a hard failover after the primary has already failed:

- **Producer-side:** any in-flight, unacknowledged sends are lost.
- **Consumer-side:** as above, plus any messages produced after the last successful MM2 replication batch are not on the secondary at all and will appear when the primary comes back.

## Failback

When the primary is restored, do not immediately switch back. Instead:

1. Let the primary catch up by reversing MM2's direction temporarily (or running a one-off batch sync).
2. Verify both clusters are in sync.
3. Schedule failback during a low-traffic window.
4. Repeat the procedure above in reverse.

Failing back hastily creates the same split-brain risk as failing over.

## Communication

During an active failover:

- Open an incident channel
- Post status updates every 15 minutes
- Track: time of detection, time of failover decision, time of restoration, estimated data loss
- Write a postmortem within 5 business days

## When NOT to follow this runbook

- Practice DR drills should follow this runbook exactly so the muscle memory exists when it matters
- A real production incident may require deviation; document the deviation in the postmortem
- If you are unsure whether to failover, escalate to whoever owns the data platform on-call rotation

