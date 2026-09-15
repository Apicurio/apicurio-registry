package io.apicurio.registry.events.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RuleViolationTest {

    @Test
    void testSerialization_roundTripsRuleTypeAndViolations() throws Exception {
        RuleViolation data = new RuleViolation();
        data.setGroupId("my-group");
        data.setArtifactId("my-artifact");
        data.setVersion("1");
        data.setRuleType("COMPATIBILITY");
        data.setViolations(List.of("Removed field 'foo' is not backwards compatible"));

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(data);
        RuleViolation roundTripped = mapper.readValue(json, RuleViolation.class);

        assertEquals("my-group", roundTripped.getGroupId());
        assertEquals("my-artifact", roundTripped.getArtifactId());
        assertEquals("1", roundTripped.getVersion());
        assertEquals("COMPATIBILITY", roundTripped.getRuleType());
        assertEquals(1, roundTripped.getViolations().size());
        assertEquals("Removed field 'foo' is not backwards compatible", roundTripped.getViolations().get(0));
    }
}
