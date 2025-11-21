package io.apicurio.registry.utils.tests.infra;

import io.apicurio.registry.utils.tests.TestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.utils.tests.infra.Configs.REGISTRY_V3_IMAGE;
import static io.apicurio.registry.utils.tests.infra.Configs.REGISTRY_V3_IMAGE_DEFAULT;

public final class RegistryV3KafkaSQLInfra extends AbstractRegistryInfra {

    private static final Logger log = LoggerFactory.getLogger(RegistryV3KafkaSQLInfra.class);

    private static RegistryV3KafkaSQLInfra instance;

    public static RegistryV3KafkaSQLInfra getInstance() {
        if (instance == null) {
            instance = new RegistryV3KafkaSQLInfra();
        }
        return instance;
    }

    private RegistryV3KafkaSQLInfra() {
        super("Registry v3");
    }

    public boolean start(String bootstrapServers) {
        return start(bootstrapServers, Map.of());
    }

    public boolean start(String bootstrapServers, Map<String, String> env) {
        var envWithDefaults = new HashMap<>(env);
        Map.of(
                "APICURIO_STORAGE_KIND", "kafkasql",
                "APICURIO_KAFKASQL_BOOTSTRAP_SERVERS", bootstrapServers
        ).forEach(envWithDefaults::putIfAbsent);
        return startRegistryContainer(System.getProperty(REGISTRY_V3_IMAGE, REGISTRY_V3_IMAGE_DEFAULT), envWithDefaults);
    }

    public String getRegistryV3ApiUrl() {
        return TestUtils.getRegistryV3ApiUrl(registryContainer.getMappedPort(8080));
    }
}
