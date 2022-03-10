package io.apicurio.registry.systemtest;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryBuilder;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
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
        ResourceManager.getInstance().create(createSR());
    }

    @Test
    public void simpleTestCreateMy() {
        Namespace n = new NamespaceBuilder().withNewMetadata().withName("rkubis").endMetadata().build();

        ResourceManager.getInstance().create(n);
    }

    @Test
    public void simpleTestDelete() {
        ResourceManager.getInstance().delete(createSR());
    }
}
