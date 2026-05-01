package com.streamflow.producer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Publishes domain events to the primary Kafka cluster.
 *
 * Partition key strategy: tenantId.
 * Guarantees in-order processing per tenant. Cross-tenant ordering is
 * intentionally not preserved.
 *
 * Risk: hot partitions if tenant traffic is heavily skewed. Per-partition
 * metrics expose the skew.
 *
 * Idempotency key: each event carries a UUID to allow downstream
 * consumers to dedupe in case of redelivery during failover.
 */
@Service
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private static final String EVENTS_TOPIC = "events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Counter publishedCounter;
    private final Counter failedCounter;
    private final Timer publishLatency;

    public EventPublisher(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry registry) {
        this.kafkaTemplate = kafkaTemplate;
        this.publishedCounter = Counter.builder("streamflow.events.published")
                .description("Total events successfully published")
                .register(registry);
        this.failedCounter = Counter.builder("streamflow.events.failed")
                .description("Total event publish failures after all retries")
                .register(registry);
        this.publishLatency = Timer.builder("streamflow.events.publish.latency")
                .description("End-to-end publish latency including broker ack")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public CompletableFuture<SendResult<String, String>> publish(EventRequest request) {
        String idempotencyKey = UUID.randomUUID().toString();
        Timer.Sample sample = Timer.start();

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate
                .send(EVENTS_TOPIC, request.tenantId(), request.toJson(idempotencyKey));

        future.whenComplete((result, ex) -> {
            sample.stop(publishLatency);
            if (ex != null) {
                failedCounter.increment();
                log.error("Failed to publish event for tenant={} key={}",
                        request.tenantId(), idempotencyKey, ex);
            } else {
                publishedCounter.increment();
                log.debug("Published tenant={} partition={} offset={}",
                        request.tenantId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }
}