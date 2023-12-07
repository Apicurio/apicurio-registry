package io.apicurio.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.APPLICATION_KAFKA_SECURED_RESOURCES;
import static io.apicurio.deployment.KubernetesTestResources.KAFKA_RESOURCES;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;

public class KafkaSqlDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaSqlDeploymentManager.class);

    static void deployKafkaApp(String registryImage) throws Exception {
        if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
            prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_SECURED_RESOURCES, true, registryImage);
        } else {
            prepareTestsInfra(KAFKA_RESOURCES, APPLICATION_KAFKA_RESOURCES, false, registryImage);
        }
    }
}
