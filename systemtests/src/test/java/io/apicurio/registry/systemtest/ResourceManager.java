package io.apicurio.registry.systemtest;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryBuilder;
import io.fabric8.kubernetes.client.internal.SerializationUtils;

public class ResourceManager {
    private ApicurioRegistry createSR() {
        return new ApicurioRegistryBuilder()
                .withNewMetadata()
                .withName("reg-test")
                .endMetadata()
                .withNewSpec()
                .withNewConfiguration()
                .withPersistence("kafkasql")
                .withNewKafkasql()
                .withBootstrapServers("my-cluster-kafka-bootstrap.registry-example-kafkasql-plain.svc:9092")
                .endKafkasql()
                .endConfiguration()
                .endSpec()
                .build();
    }

    private static ResourceManager instance;

    public static ResourceManager getInstance() {
        if (instance == null) {
            instance = new ResourceManager();
        }
        return instance;
    }

    public final ApicurioRegistry getServiceRegistry() {
        return createSR();
    }

    public final void createResource() {
        resourceClient.inNamespace("apicurio-test").createOrReplace(ap);
    }

    public final void deleteResource() {
        resourceClient.inNamespace("apicurio-test").createOrReplace(ap);
    }
}
