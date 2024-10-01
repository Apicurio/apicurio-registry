package io.apicurio.registry.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.apicurio.registry.storage.dto.OutboxEvent;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.ARTIFACT_DELETED;

public class ArtifactDeleted extends OutboxEvent {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final JsonNode eventPayload;

    private ArtifactDeleted(String id, String aggregateId, JsonNode eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ArtifactDeleted of(String groupId, String artifactId) {
        String id = UUID.randomUUID().toString();
        // TODO here we have to define the internal structure of the event, maybe use cloudevents?
        ObjectNode asJson = mapper.createObjectNode().put("id", id).put("groupId", groupId)
                .put("artifactId", artifactId).put("eventType", ARTIFACT_DELETED.name());

        return new ArtifactDeleted(id, groupId + "-" + artifactId, asJson);
    }

    @Override
    public String getType() {
        return ARTIFACT_DELETED.name();
    }

    @Override
    public JsonNode getPayload() {
        return eventPayload;
    }
}
