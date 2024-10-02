package io.apicurio.registry.storage.dto;

import org.json.JSONObject;

public abstract class OutboxEvent {

    private final String id;
    private final String aggregateId;

    protected OutboxEvent(String id, String aggregateId) {
        this.id = id;
        this.aggregateId = aggregateId;
    }

    public String getId() {
        return this.id;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getAggregateType() {
        return "registry-events";
    }

    public abstract JSONObject getPayload();

    public abstract String getType();
}
