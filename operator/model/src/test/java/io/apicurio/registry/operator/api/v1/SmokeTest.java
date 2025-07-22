package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.operator.api.v1.status.Condition;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static io.apicurio.registry.operator.api.v1.ApicurioRegistry3Status.isEquivalent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmokeTest {

    static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

    @Test
    public void basicReadOfCRs() throws Exception {
        // Arrange
        var resource = getClass().getResourceAsStream("/demo-cr.yaml");

        // Act
        var registry = MAPPER.readValue(resource, ApicurioRegistry3.class);

        // Assert
        assertEquals("test", registry.getMetadata().getName());
        assertEquals("test-namespace", registry.getMetadata().getNamespace());
    }

    @Test
    public void basicReadOfMultipleCRs() throws Exception {
        // Arrange
        var resource = getClass().getResourceAsStream("/demo-crs.yaml");

        // Act
        var registries = MAPPER
                .readValues(MAPPER.createParser(resource), new TypeReference<ApicurioRegistry3>() {
                }).readAll();

        // Assert
        assertEquals("test1", registries.get(0).getMetadata().getName());
        assertEquals("test-namespace1", registries.get(0).getMetadata().getNamespace());
        assertEquals("test2", registries.get(1).getMetadata().getName());
        assertEquals("test-namespace2", registries.get(1).getMetadata().getNamespace());
    }

    @Test
    public void isEquivalentTest() {
        assertTrue(isEquivalent(null, null));
        assertFalse(isEquivalent(null, new ApicurioRegistry3Status()));
        assertTrue(isEquivalent(
                ApicurioRegistry3Status.builder()
                        .observedGeneration(1L)
                        .conditions(List.of())
                        .build(),
                ApicurioRegistry3Status.builder()
                        .observedGeneration(2L)
                        .build()
        ));
        assertTrue(isEquivalent(
                ApicurioRegistry3Status.builder()
                        .conditions(List.of(
                                Condition.builder()
                                        .lastUpdateTime(Instant.MIN)
                                        .reason("foo")
                                        .build(),
                                Condition.builder()
                                        .lastUpdateTime(Instant.MAX)
                                        .reason("bar")
                                        .build()
                        )).build(),
                ApicurioRegistry3Status.builder()
                        .conditions(List.of(
                                Condition.builder()
                                        .lastUpdateTime(Instant.MIN)
                                        .reason("bar")
                                        .build(),
                                Condition.builder()
                                        .lastUpdateTime(Instant.MAX)
                                        .reason("foo")
                                        .build()
                        )).build()
        ));
        assertFalse(isEquivalent(
                ApicurioRegistry3Status.builder()
                        .conditions(List.of(
                                Condition.builder()
                                        .lastUpdateTime(Instant.MIN)
                                        .reason("foo")
                                        .message("baz")
                                        .build(),
                                Condition.builder()
                                        .lastUpdateTime(Instant.MAX)
                                        .reason("bar")
                                        .build()
                        )).build(),
                ApicurioRegistry3Status.builder()
                        .conditions(List.of(
                                Condition.builder()
                                        .lastUpdateTime(Instant.MIN)
                                        .reason("bar")
                                        .build(),
                                Condition.builder()
                                        .lastUpdateTime(Instant.MAX)
                                        .reason("foo")
                                        .build()
                        )).build()
        ));
    }
}
