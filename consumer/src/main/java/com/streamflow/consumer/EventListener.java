package main.java.com.streamflow.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Service
public class EventListener {

    private static final Logger log = LoggerFactory.getLogger(EventListener.class);

    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Timer processingLatency;

    public EventListener(MeterRegistry registry) {
        this.processedCounter = Counter.builder("streamflow.events.processed")
                .description("Events successfully processed and acked")
                .register(registry);
        this.failedCounter = Counter.builder("streamflow.events.processing.failed")
                .description("Events that failed processing")
                .register(registry);
        this.processingLatency = Timer.builder("streamflow.events.processing.latency")
                .description("Time spent processing each event before ack")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @KafkaListener(
            topics = "${streamflow.consumer.topic}",
            groupId = "${streamflow.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        Timer.Sample sample = Timer.start();
        MDC.put("partition", String.valueOf(record.partition()));
        MDC.put("offset", String.valueOf(record.offset()));
        MDC.put("tenantId", record.key());

        try {
            log.info("Processing event tenant={} partition={} offset={}",
                    record.key(), record.partition(), record.offset());

            processEvent(record);

            ack.acknowledge();
            processedCounter.increment();
        } catch (Exception e) {
            failedCounter.increment();
            log.error("Failed to process event", e);
            throw e;
        } finally {
            sample.stop(processingLatency);
            MDC.clear();
        }
    }

    private void processEvent(ConsumerRecord<String, String> record) {
        // Real downstream work would go here.
    }
}