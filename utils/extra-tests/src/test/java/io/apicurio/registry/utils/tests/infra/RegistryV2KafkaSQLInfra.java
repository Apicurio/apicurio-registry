package io.apicurio.registry.utils.tests.infra;

import java.util.Map;

import static io.apicurio.registry.utils.tests.infra.Configs.REGISTRY_V2_KAFKASQL_IMAGE;
import static io.apicurio.registry.utils.tests.infra.Configs.REGISTRY_V2_KAFKASQL_IMAGE_DEFAULT;

public final class RegistryV2KafkaSQLInfra extends AbstractRegistryInfra {

    private static RegistryV2KafkaSQLInfra instance;

    public static RegistryV2KafkaSQLInfra getInstance() {
        if (instance == null) {
            instance = new RegistryV2KafkaSQLInfra();
        }
        return instance;
    }

    private RegistryV2KafkaSQLInfra() {
        super("Registry v2");
    }

    public boolean start(String bootstrapServers) {
        return startRegistryContainer(System.getProperty(REGISTRY_V2_KAFKASQL_IMAGE, REGISTRY_V2_KAFKASQL_IMAGE_DEFAULT), Map.of(
                "KAFKA_BOOTSTRAP_SERVERS", bootstrapServers,
                "REGISTRY_APIS_V2_DATE_FORMAT", "yyyy-MM-dd'T'HH:mm:ss'Z'"
        ));
    }
}
