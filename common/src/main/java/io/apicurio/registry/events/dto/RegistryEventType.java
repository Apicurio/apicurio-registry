package io.apicurio.registry.events.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public enum RegistryEventType {

    GROUP_CREATED, GROUP_UPDATED, GROUP_DELETED,

    ARTIFACTS_IN_GROUP_DELETED,

    ARTIFACT_CREATED, ARTIFACT_UPDATED, ARTIFACT_DELETED,

    ARTIFACT_STATE_CHANGED,

    ARTIFACT_RULE_CREATED, ARTIFACT_RULE_UPDATED, ARTIFACT_RULE_DELETED, ALL_ARTIFACT_RULES_DELETED,

    GLOBAL_RULE_CREATED, GLOBAL_RULE_UPDATED, GLOBAL_RULE_DELETED, ALL_GLOBAL_RULES_DELETED;

    private String cloudEventType;

    private RegistryEventType() {
        this.cloudEventType = "io.apicurio.registry." + this.name().toLowerCase().replace("_", "-");
    }

    public String cloudEventType() {
        return this.cloudEventType;
    }
}
