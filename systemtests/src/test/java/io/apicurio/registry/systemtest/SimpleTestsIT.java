package io.apicurio.registry.systemtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.operator.api.model.ApicurioRegistryBuilder;
import io.apicurio.registry.operator.api.model.ApicurioRegistryList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.internal.SerializationUtils;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SimpleTestsIT {
    private static Logger LOGGER = LoggerFactory.getLogger(SimpleTestsIT.class);

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

    //INFO: In order to get this test working operator has to be deployed first.
    @Test
    public void simpleTestIT() {
        LOGGER.info("First test log!");
        assertThat("123", is("123"));
        Config config = Config.autoConfigure(System.getenv()
                .getOrDefault("TEST_CLUSTER_CONTEXT", null));

        try (OpenShiftClient ocClient = new DefaultOpenShiftClient(new OpenShiftConfig(config))) {
            ApicurioRegistry ap = createSR();

            MixedOperation<ApicurioRegistry, ApicurioRegistryList, Resource<ApicurioRegistry>> resourceClient =
                    ocClient.resources(ApicurioRegistry.class, ApicurioRegistryList.class);

            String yaml = SerializationUtils.dumpAsYaml(ap);
            resourceClient.inNamespace("apicurio-test").createOrReplace(ap);
            LOGGER.info("Yaml file deployemnt:\n\n " + yaml);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
