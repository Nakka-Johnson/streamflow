# Roadmap

## Phase 1: Foundation (complete)
- [x] Two-cluster Kafka topology in KRaft mode
- [x] Idempotent producer with `acks=all`
- [x] Consumer with manual offset commit and DLQ routing
- [x] Topic creation scripts

## Phase 2: Cross-Cluster Replication (complete)
- [x] MirrorMaker 2 standalone mode between primary and secondary
- [x] Source/checkpoint/heartbeat connector configuration
- [x] Topic naming and offset translation verification
- [x] Failover simulation script
- [x] Failover runbook documentation
- [x] Architecture deep-dive document

## Phase 3: Observability and Quality (complete)
- [x] Prometheus + Grafana with producer and consumer dashboards
- [x] Embedded Kafka integration tests for producer and consumer
- [x] GitHub Actions CI workflow
- [x] Load testing script
- [x] README polish with screenshots and CI badge

## Future work (not planned for current scope)
- Kafka broker JMX metrics via custom Dockerfile + JMX Prometheus agent
- Schema Registry + Avro for schema evolution
- Active-active replication topology with conflict resolution
- Automated chaos testing (kill a broker mid-replication)
- mTLS authentication between brokers and clients
- Cluster Linking comparison once available outside Confluent Cloud