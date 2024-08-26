package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SerDeTest {

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
}
