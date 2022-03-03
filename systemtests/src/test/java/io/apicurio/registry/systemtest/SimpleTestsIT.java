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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SimpleTestsIT {
    private static Logger LOGGER = LoggerFactory.getLogger(SimpleTestsIT.class);

    @Test
    public void simpleTestCreate() {
        Config config = Config.autoConfigure(System.getenv()
                .getOrDefault("TEST_CLUSTER_CONTEXT", null));

        try (OpenShiftClient ocClient = new DefaultOpenShiftClient(new OpenShiftConfig(config))) {
            ApicurioRegistry ap = ResourceManager.getInstance().getServiceRegistry();

            MixedOperation<ApicurioRegistry, ApicurioRegistryList, Resource<ApicurioRegistry>> resourceClient =
                    ocClient.resources(ApicurioRegistry.class, ApicurioRegistryList.class);


        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void simpleTestDelete() {
        Config config = Config.autoConfigure(System.getenv()
                .getOrDefault("TEST_CLUSTER_CONTEXT", null));

        try (OpenShiftClient ocClient = new DefaultOpenShiftClient(new OpenShiftConfig(config))) {
            ApicurioRegistry ap = ResourceManager.getInstance().getServiceRegistry();

            MixedOperation<ApicurioRegistry, ApicurioRegistryList, Resource<ApicurioRegistry>> resourceClient =
                    ocClient.resources(ApicurioRegistry.class, ApicurioRegistryList.class);

            String yaml = SerializationUtils.dumpAsYaml(ap);
            resourceClient.inNamespace("apicurio-test").delete(ap);
            LOGGER.info("Yaml file deployment:\n\n " + yaml);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}
