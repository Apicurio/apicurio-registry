package io.apicurio.registry.federation;

import io.apicurio.registry.utils.tests.KafkasqlTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * KafkaSQL storage with federation enabled.
 */
public class FederationKafkasqlTestProfile extends KafkasqlTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>(super.getConfigOverrides());
        props.putAll(FederationEnabledProfile.federationOverrides());
        return props;
    }
}
