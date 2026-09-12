package io.apicurio.registry.storage.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RuleConfigurationDtoTest {

    @Test
    public void testParseOnFailureDefaultsUnsupportedValuesToError() {
        assertEquals(RuleAction.NONE, RuleConfigurationDto.parseOnFailure(RuleAction.NONE.name()));
        assertEquals(RuleAction.ERROR, RuleConfigurationDto.parseOnFailure(null));
        assertEquals(RuleAction.ERROR, RuleConfigurationDto.parseOnFailure(RuleAction.DLQ.name()));
        assertEquals(RuleAction.ERROR, RuleConfigurationDto.parseOnFailure("invalid"));
    }

    @Test
    public void testGetOnFailureDefaultsUnsupportedValuesToError() {
        RuleConfigurationDto dto = new RuleConfigurationDto();
        dto.setOnFailure(RuleAction.DLQ);

        assertEquals(RuleAction.ERROR, dto.getOnFailure());
    }
}
