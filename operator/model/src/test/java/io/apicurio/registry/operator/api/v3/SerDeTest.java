package io.apicurio.registry.operator.api.v3;

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
}
