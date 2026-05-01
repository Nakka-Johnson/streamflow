package com.streamflow.producer;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventPublisher publisher;

    public EventController(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> publish(@RequestBody EventRequest request) {
        return publisher.publish(request)
                .thenApply(result -> ResponseEntity.accepted().body(Map.of(
                        "topic", result.getRecordMetadata().topic(),
                        "partition", result.getRecordMetadata().partition(),
                        "offset", result.getRecordMetadata().offset(),
                        "tenantId", request.tenantId()
                )));
    }
}