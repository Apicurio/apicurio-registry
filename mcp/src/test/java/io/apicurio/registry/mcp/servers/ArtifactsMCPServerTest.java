package io.apicurio.registry.mcp.servers;

import io.quarkiverse.mcp.server.ToolArg;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArtifactsMCPServerTest {

    @Test
    void createArtifactRequiresTheArtifactType() {
        Method method = Arrays.stream(ArtifactsMCPServer.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("create_artifact"))
                .findFirst().orElseThrow();
        Map<String, Boolean> required = Arrays.stream(method.getParameters())
                .collect(Collectors.toMap(Parameter::getName, p -> p.getAnnotation(ToolArg.class).required()));
        assertEquals(Map.of("groupId", true, "artifactId", true, "artifactType", true,
                "name", false, "description", false, "jsonLabels", false), required);
    }
}
