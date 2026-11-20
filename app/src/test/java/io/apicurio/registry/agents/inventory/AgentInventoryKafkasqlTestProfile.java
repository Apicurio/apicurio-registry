package io.apicurio.registry.agents.inventory;

import io.apicurio.registry.utils.tests.KafkasqlTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaSQL storage with inventory sync enabled, as in {@link AgentInventoryTestProfile}.
 */
public class AgentInventoryKafkasqlTestProfile extends KafkasqlTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>(super.getConfigOverrides());
        props.putAll(AgentInventoryTestProfile.inventoryOverrides());
        return props;
    }
}
