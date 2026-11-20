package io.apicurio.registry.mcp;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DescriptionsTest {

    // Media types emitted by AiCatalogConstants in the app module; a type filter must match one of them.
    private static final List<String> EMITTED_TYPES = List.of(
            "application/a2a-agent-card+json",
            "application/mcp-server-card+json");

    @Test
    void ardSearchTypeExampleUsesEmittedTypes() {
        Matcher matcher = Pattern.compile("e\\.g\\. \"([^\"]+)\"").matcher(Descriptions.ARD_SEARCH_TYPE);
        assertEquals(true, matcher.find());
        List<String> examples = Arrays.asList(matcher.group(1).split(","));
        assertEquals(EMITTED_TYPES, examples);
    }
}
