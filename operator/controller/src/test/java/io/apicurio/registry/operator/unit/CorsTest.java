package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.Cors;
import io.apicurio.registry.operator.resource.ResourceFactory;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Set;

public class CorsTest {

    @Test
    public void testConfigureAllowedOrigins() throws Exception {
        doTestAllowedOrigins("k8s/examples/cors/example-default.yaml", "*");
        doTestAllowedOrigins("k8s/examples/cors/example-ingress.yaml",
                "https://simple-ui.apps.cluster.example", "https://simple-ui.apps.cluster.example");
        doTestAllowedOrigins("k8s/examples/cors/example-env-vars.yaml", "https://ui.example.org");
        doTestAllowedOrigins("k8s/examples/cors/example-env-vars-and-ingress.yaml", "https://ui.example.org");
    }

    private void doTestAllowedOrigins(String crPath, String... values) {
        ClassLoader classLoader = CorsTest.class.getClassLoader();
        ApicurioRegistry3 registry = ResourceFactory.deserialize(crPath, ApicurioRegistry3.class,
                classLoader);

        LinkedHashMap<String, EnvVar> envVars = new LinkedHashMap<>();
        Cors.configureAllowedOrigins(registry, envVars);
        Assertions.assertThat(envVars.keySet()).contains(EnvironmentVariables.QUARKUS_HTTP_CORS_ORIGINS);
        String allowedOriginsValue = envVars.get(EnvironmentVariables.QUARKUS_HTTP_CORS_ORIGINS).getValue();
        Set<String> allowedOrigins = Set.of(allowedOriginsValue.split(","));
        Assertions.assertThat(allowedOrigins).containsExactlyInAnyOrder(values);
    }

    public static String load(String path) {
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), Charset.defaultCharset());
        } catch (Exception ex) {
            throw new OperatorException("Could not read resource: " + path, ex);
        }
    }

}
