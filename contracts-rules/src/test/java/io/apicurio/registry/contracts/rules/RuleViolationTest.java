package io.apicurio.registry.contracts.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleViolationTest {

    @Test
    void testToStringNamesTheRuleTheActionAndTheMessage() {
        RuleViolation violation = new RuleViolation("positive-amount", "Condition failed: positive-amount", "ERROR");

        assertEquals("positive-amount (ERROR): Condition failed: positive-amount", violation.toString());
        assertEquals("[positive-amount (ERROR): Condition failed: positive-amount]", List.of(violation).toString());
    }
}
