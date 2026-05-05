package com.streamflow.producer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.Consumer;
import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {"events"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class EventPublisherIntegrationTest {

    @Value("${spring.embedded.kafka.brokers}")
    private String embeddedBrokers;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private EventPublisher publisher;

    @Test
    @DisplayName("Single event publishes successfully and lands on the events topic")
    void shouldPublishSingleEvent() throws Exception {
        EventRequest request = new EventRequest(
                "tenant-001", "ORDER_CREATED", MAPPER.readTree("{\"orderId\":\"ord-1\"}"));

        SendResult<String, String> result = publisher.publish(request).get(10, TimeUnit.SECONDS);

        assertNotNull(result);
        assertEquals("events", result.getRecordMetadata().topic());
        assertTrue(result.getRecordMetadata().offset() >= 0);
        assertTrue(result.getRecordMetadata().partition() >= 0);
    }

    @Test
    @DisplayName("Events with the same tenant key land on the same partition")
    void shouldPartitionByTenantId() throws Exception {
        String tenantId = "tenant-consistent";
        List<Integer> partitions = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
                EventRequest request = new EventRequest(
                    tenantId,
                    "ORDER_CREATED",
                    MAPPER.readTree("{\"orderId\":\"ord-" + i + "\"}"));
            SendResult<String, String> result =
                    publisher.publish(request).get(10, TimeUnit.SECONDS);
            partitions.add(result.getRecordMetadata().partition());
        }

        long distinctPartitions = partitions.stream().distinct().count();
        assertEquals(1, distinctPartitions,
                "Expected all events for the same tenant to share a partition");
    }

    @Test
    @DisplayName("Published event payload is consumable end-to-end")
    void shouldRoundTripPayload() throws Exception {
        EventRequest request = new EventRequest(
                "tenant-roundtrip", "TEST_EVENT", MAPPER.readTree("{\"data\":\"hello\"}"));
        publisher.publish(request).get(10, TimeUnit.SECONDS);

        Properties consumerProps = new Properties();
        consumerProps.put(BOOTSTRAP_SERVERS_CONFIG, embeddedBrokers);
        consumerProps.put(GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        consumerProps.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(AUTO_OFFSET_RESET_CONFIG, "earliest");

        try (Consumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(Collections.singleton("events"));
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

            boolean found = false;
            for (ConsumerRecord<String, String> rec : records) {
                if (rec.key().equals("tenant-roundtrip") && rec.value().contains("hello")) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Expected to consume the round-trip event");
        }
    }
}
