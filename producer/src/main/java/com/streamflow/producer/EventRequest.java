package com.streamflow.producer;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record EventRequest(String tenantId, String type, JsonNode payload) {

    public String toJson(String idempotencyKey) {
        String payloadJson = payload == null ? "null" : payload.toString();
        return String.format(
                "{\"idempotencyKey\":\"%s\",\"tenantId\":\"%s\",\"type\":\"%s\"," +
                "\"payload\":%s,\"timestamp\":\"%s\"}",
                idempotencyKey, tenantId, type, payloadJson, Instant.now()
        );
    }
}