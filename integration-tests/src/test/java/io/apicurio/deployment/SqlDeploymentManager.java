package io.apicurio.deployment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.apicurio.deployment.KubernetesTestResources.*;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;

public class SqlDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlDeploymentManager.class);

    protected static void deploySqlApp(String registryImage) throws Exception {
        if (Constants.TEST_PROFILE.equals(Constants.AUTH)) {
            prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_SECURED_RESOURCES, true, registryImage);
        } else {
            prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_RESOURCES, false, registryImage);
        }
    }
}
