cat > docs/ROADMAP.md << 'EOF'
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

## Phase 3: Observability and Quality (planned)
- [ ] Prometheus + Grafana with broker, producer, consumer dashboards
- [ ] Per-partition consumer lag monitoring
- [ ] Testcontainers integration tests
- [ ] Load testing scripts
EOF