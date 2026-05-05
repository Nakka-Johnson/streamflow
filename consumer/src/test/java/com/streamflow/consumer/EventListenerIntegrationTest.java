package com.streamflow.consumer;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Properties;

import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the consumer using an embedded Kafka broker. Verifies that
 * events produced to the topic are processed by the listener and metrics reflect
 * the activity.
 */
@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"events"})
@TestPropertySource(properties = {
        "streamflow.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "streamflow.consumer.topic=events",
        "streamflow.consumer.group-id=test-consumer-group"
})
class EventListenerIntegrationTest {

    @Value("${spring.embedded.kafka.brokers}")
    private String embeddedBrokers;

    @Autowired
    private EventListener listener;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Listener processes published events end-to-end")
    void shouldConsumeAndProcessEvents() throws Exception {
        Properties producerProps = new Properties();
        producerProps.put(BOOTSTRAP_SERVERS_CONFIG, embeddedBrokers);
        producerProps.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ACKS_CONFIG, "all");

        try (Producer<String, String> producer = new KafkaProducer<>(producerProps)) {
            for (int i = 0; i < 3; i++) {
                producer.send(new ProducerRecord<>(
                        "events",
                        "tenant-test-" + i,
                        "{\"orderId\":\"ord-" + i + "\"}"
                )).get();
            }
        }

        Counter processed = meterRegistry.find("streamflow.events.processed").counter();
        assertNotNull(processed, "Expected streamflow.events.processed counter to be registered");

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> assertTrue(processed.count() >= 3.0));
    }
}