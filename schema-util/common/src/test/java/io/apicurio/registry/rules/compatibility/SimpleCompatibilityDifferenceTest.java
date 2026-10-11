package io.apicurio.registry.rules.compatibility;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SimpleCompatibilityDifferenceTest {

    @Test
    public void testAsRuleViolation() {
        SimpleCompatibilityDifference difference = new SimpleCompatibilityDifference("description",
                "/context");
        Assertions.assertEquals("description", difference.asRuleViolation().getDescription());
        Assertions.assertEquals("/context", difference.asRuleViolation().getContext());
    }

    @Test
    public void testContextDefaultsToRoot() {
        Assertions.assertEquals("/",
                new SimpleCompatibilityDifference("description").asRuleViolation().getContext());
        Assertions.assertEquals("/",
                new SimpleCompatibilityDifference("description", null).asRuleViolation().getContext());
        Assertions.assertEquals("/",
                new SimpleCompatibilityDifference("description", " ").asRuleViolation().getContext());
    }

    @Test
    public void testDescriptionIsRequired() {
        Assertions.assertThrows(NullPointerException.class,
                () -> new SimpleCompatibilityDifference(null, "/context"));
    }

    @Test
    public void testEqualsAndHashCode() {
        SimpleCompatibilityDifference difference = new SimpleCompatibilityDifference("description",
                "/context");
        SimpleCompatibilityDifference same = new SimpleCompatibilityDifference("description", "/context");
        SimpleCompatibilityDifference otherDescription = new SimpleCompatibilityDifference("other",
                "/context");
        SimpleCompatibilityDifference otherContext = new SimpleCompatibilityDifference("description",
                "/other");

        Assertions.assertEquals(difference, same);
        Assertions.assertEquals(difference.hashCode(), same.hashCode());
        Assertions.assertNotEquals(difference, otherDescription);
        Assertions.assertNotEquals(difference, otherContext);
        Assertions.assertNotEquals(difference, null);
    }
}
