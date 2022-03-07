package io.apicurio.registry.systemtest;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTestsIT {
    private static Logger LOGGER = LoggerFactory.getLogger(SimpleTestsIT.class);

    private ApicurioRegistry createSR() {
        return new ApicurioRegistryBuilder()
                .withNewMetadata()
                .withName("reg-test")
                .withNamespace("apicurio-test")
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

    @Test
    public void simpleTestCreate() {
        ApicurioRegistryResourceManager.getInstance().create(createSR());
    }

    @Test
    public void simpleTestDelete() {
        ApicurioRegistryResourceManager.getInstance().delete(createSR());
    }
}
