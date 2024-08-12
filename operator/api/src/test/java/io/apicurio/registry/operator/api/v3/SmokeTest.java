package io.apicurio.registry.operator.api.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3Builder;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3List;
import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3ListBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
class SmokeTest {

    public static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper(new YAMLFactory());
    }

    private static final Logger log = LoggerFactory.getLogger(SmokeTest.class);

    @Test
    void simpleSerDesTest() throws IOException {

        // @formatter:off
        // spotless:off
        var as1 = new ApicurioRegistry3Builder()
                .withNewMetadata()
                    .withName("test")
                    .withNamespace("test-namespace")
                .endMetadata()
                .withNewSpec()
                    .withNewApp()
                        .withEnv(new EnvVarBuilder()
                            .withName("foo1")
                            .withValue("bar1")
                            .build()
                        )
                        .withNewPodTemplate()
                            .withNewSpec()
                                .withContainers(new ContainerBuilder()
                                    .withName("apicurio-registry-app")
                                    .withNewResources()
                                        .addToRequests("cpu", Quantity.parse("199m"))
                                        .addToRequests("memory", Quantity.parse("499Mi"))
                                    .endResources()
                                    .build()
                                )
                            .endSpec()
                        .endPodTemplate()
                        .withHost("test-app.cluster.example")
                    .endApp()
                    .withNewUi()
                        .withEnv(new EnvVarBuilder()
                            .withName("foo2")
                            .withValue("bar2")
                            .build()
                        )
                        .withHost("test-ui.cluster.example")
                    .endUi()
                .endSpec()
                .build();
        // spotless:on
        // @formatter:on

        var as2 = MAPPER.readValue(getClass().getResourceAsStream("/smoke-apicurioregistry3.yaml"),
                ApicurioRegistry3.class);

        Assertions.assertEquals(as1, as2);
        as1.getSpec().getApp().getEnv().get(0).setName("bad1");
        Assertions.assertNotEquals(as1, as2);
        as1.getSpec().getApp().getEnv().get(0).setName("foo1");

        // LIST

        var asl1 = new ApicurioRegistry3ListBuilder().withItems(as1).build();

        var asl2 = MAPPER.readValue(getClass().getResourceAsStream("/smoke-apicurioregistry3-list.yaml"),
                ApicurioRegistry3List.class);

        Assertions.assertEquals(asl1, asl2);
    }
}
