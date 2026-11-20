package io.apicurio.registry.events;

import io.apicurio.registry.storage.dto.OutboxEvent;
import org.json.JSONObject;

import java.util.UUID;

import static io.apicurio.registry.storage.StorageEventType.CONTRACT_RULESET_CONFIGURED;

public class ContractRulesetConfigured extends OutboxEvent {

    /**
     * Sentinel carried as both groupId and artifactId when the event describes the global (not artifact
     * scoped) contract ruleset. Global events do not use null or empty coordinates, so a consumer that
     * needs to detect global scope must compare both coordinates against this constant rather than
     * testing for absence. Every storage variant emits the same value.
     */
    public static final String GLOBAL_COORDINATE = "__GLOBAL__";

    /**
     * The mutation that produced the event. The enum name is what appears in the "action" payload field,
     * so consumers switching on that field see exactly these values.
     */
    public enum Action {
        SET, DELETE
    }

    private final JSONObject eventPayload;

    private ContractRulesetConfigured(String id, String aggregateId, JSONObject eventPayload) {
        super(id, aggregateId);
        this.eventPayload = eventPayload;
    }

    public static ContractRulesetConfigured of(String groupId, String artifactId,
            String version, Action action) {
        String id = UUID.randomUUID().toString();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("id", id)
                .put("groupId", groupId)
                .put("artifactId", artifactId)
                .put("action", action.name())
                .put("eventType", CONTRACT_RULESET_CONFIGURED.name());
        if (version != null) {
            jsonObject.put("version", version);
        }
        return new ContractRulesetConfigured(id, groupId + "-" + artifactId, jsonObject);
    }

    /**
     * Builds an event for the global contract ruleset, using {@link #GLOBAL_COORDINATE} for both
     * coordinates so no caller has to repeat the sentinel.
     */
    public static ContractRulesetConfigured ofGlobal(Action action) {
        return of(GLOBAL_COORDINATE, GLOBAL_COORDINATE, null, action);
    }

    @Override
    public String getType() {
        return CONTRACT_RULESET_CONFIGURED.name();
    }

    @Override
    public JSONObject getPayload() {
        return eventPayload;
    }
}
