package io.apicurio.registry.systemtest;

import io.apicurio.registry.operator.api.model.ApicurioRegistry;
import io.apicurio.registry.systemtest.messaginginfra.ResourceManager;
import io.apicurio.registry.systemtest.messaginginfra.resources.ApicurioRegistryResourceType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTestsIT extends BaseTest {
    private static Logger LOGGER = LoggerFactory.getLogger(SimpleTestsIT.class);

    @BeforeAll
    public static void prepareInfra() {
    }

    @AfterAll
    public static void destroyInfra() {
    }

    @Test
    public void simpleTest() {
        ApicurioRegistry ar = ApicurioRegistryResourceType.getDefault();

        try {
            ResourceManager.getInstance().createResource("SimpleTestIT.simpleTest", true, ar);
        } catch (Exception e) {
            e.printStackTrace();
        }

        LOGGER.info("Resource: {}", ApicurioRegistryResourceType.getOperation().inNamespace(ar.getMetadata().getNamespace()).withName(ar.getMetadata().getName()).get().toString());

        ResourceManager.getInstance().deleteResources("SimpleTestIT.simpleTest");
    }
}
