/*
 * Copyright 2026 The Apicurio Authors
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.apicurio.registry.events.dto;

import io.apicurio.registry.events.ArtifactRuleConfigured;
import io.apicurio.registry.storage.dto.RuleConfigurationDto;
import io.apicurio.registry.types.RuleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ArtifactRuleConfiguredCloudEventTest {

    @Test
    public void testFromArtifactRuleConfigured() {
        RuleConfigurationDto rule = new RuleConfigurationDto();
        rule.setConfiguration("test-config");

        ArtifactRuleConfigured event = ArtifactRuleConfigured.of("test-group", "test-artifact", RuleType.COMPATIBILITY, rule);
        String source = "/apicurio-registry";

        ArtifactRuleConfiguredCloudEvent cloudEvent = ArtifactRuleConfiguredCloudEvent.from(event, source);

        assertNotNull(cloudEvent);
        assertNotNull(cloudEvent.getCloudEvent());
        assertEquals(event.getId(), cloudEvent.getCloudEvent().getId());
        assertEquals(source, cloudEvent.getCloudEvent().getSource());
        assertEquals("io.apicurio.registry.events.ArtifactRuleConfigured", cloudEvent.getCloudEvent().getType());
        assertEquals("1.0", cloudEvent.getCloudEvent().getSpecversion());
        assertEquals("application/json", cloudEvent.getCloudEvent().getDatacontenttype());
        assertNotNull(cloudEvent.getCloudEvent().getTime());
        assertNotNull(cloudEvent.getCloudEvent().getData());
    }

    @Test
    public void testCloudEventTypeFormat() {
        RuleConfigurationDto rule = new RuleConfigurationDto();
        rule.setConfiguration("test-config");

        ArtifactRuleConfigured event = ArtifactRuleConfigured.of("test-group", "test-artifact", RuleType.COMPATIBILITY, rule);
        ArtifactRuleConfiguredCloudEvent cloudEvent = ArtifactRuleConfiguredCloudEvent.from(event, "/apicurio-registry");

        assertEquals("io.apicurio.registry.events.ArtifactRuleConfigured", cloudEvent.getCloudEvent().getType());
    }
}
