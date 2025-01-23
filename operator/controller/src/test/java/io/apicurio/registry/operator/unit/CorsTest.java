package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.EnvironmentVariables;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.feat.Cors;
import io.apicurio.registry.operator.utils.Mapper;
import io.fabric8.kubernetes.api.model.EnvVar;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Set;

public class CorsTest {

    private static final String DEFAULT = """
            apiVersion: registry.apicur.io/v1
            kind: ApicurioRegistry3
            metadata:
              name: simple
            spec: {}
            """;

    private static final String WITH_INGRESS = """
            apiVersion: registry.apicur.io/v1
            kind: ApicurioRegistry3
            metadata:
              name: simple
            spec:
              app:
                ingress:
                  host: simple-app.apps.cluster.example
              ui:
                ingress:
                  host: simple-ui.apps.cluster.example
            """;

    private static final String WITH_ENV_VAR = """
            apiVersion: registry.apicur.io/v1
            kind: ApicurioRegistry3
            metadata:
              name: simple
            spec:
              app:
                env:
                  - name: QUARKUS_HTTP_CORS_ORIGINS
                    value: https://ui.example.org
            """;

    private static final String WITH_ENV_VAR_AND_INGRESS = """
            apiVersion: registry.apicur.io/v1
            kind: ApicurioRegistry3
            metadata:
              name: simple
            spec:
              app:
                ingress:
                  host: simple-app.apps.cluster.example
                env:
                  - name: QUARKUS_HTTP_CORS_ORIGINS
                    value: https://ui.example.org
              ui:
                ingress:
                  host: simple-ui.apps.cluster.example
            """;

    @Test
    public void testConfigureAllowedOrigins() throws Exception {
        doTestAllowedOrigins(DEFAULT, "*");
        doTestAllowedOrigins(WITH_INGRESS, "http://simple-ui.apps.cluster.example",
                "https://simple-ui.apps.cluster.example");
        doTestAllowedOrigins(WITH_ENV_VAR, "https://ui.example.org");
        doTestAllowedOrigins(WITH_ENV_VAR_AND_INGRESS, "http://simple-ui.apps.cluster.example",
                "https://simple-ui.apps.cluster.example", "https://ui.example.org");
    }

    private void doTestAllowedOrigins(String cr, String... values) {
        ApicurioRegistry3 registry = Mapper.deserialize(cr, ApicurioRegistry3.class);

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
