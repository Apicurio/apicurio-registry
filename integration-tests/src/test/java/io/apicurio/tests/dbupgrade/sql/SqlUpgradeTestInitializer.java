package io.apicurio.tests.dbupgrade.sql;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tenantmanager.api.datamodel.SortBy;
import io.apicurio.tenantmanager.api.datamodel.SortOrder;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.multitenancy.TenantUser;
import io.apicurio.tests.multitenancy.TenantUserClient;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class SqUpgradeTestInitializer implements QuarkusTestResourceLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(SqlStorageUpgradeIT.class);
    GenericContainer registryContainer;
    GenericContainer tenantManagerContainer;
    TenantManagerClient tenantManager;

    @Override
    public int order() {
        return 10000;
    }

    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {

            String jdbcUrl = System.getProperty("quarkus.datasource.jdbc.url");
            String userName = System.getProperty("quarkus.datasource.username");
            String password = System.getProperty("quarkus.datasource.password");

            String tenantManagerUrl = startTenantManagerApplication("quay.io/apicurio/apicurio-tenant-manager-api:latest", jdbcUrl, userName, password);
            String registryBaseUrl = startOldRegistryVersion("quay.io/apicurio/apicurio-registry-sql:2.1.0.Final", jdbcUrl, userName, password, tenantManagerUrl);

            try {

                //Warm up until the tenant manager is ready.
                TestUtils.retry(() -> {
                    getTenantManagerClient(tenantManagerUrl).listTenants(TenantStatusValue.READY, 0, 1, SortOrder.asc, SortBy.tenantId);
                });

                prepareData(tenantManagerUrl, registryBaseUrl);

                //Once the data is set, stop the old registry before running the tests.
                if (registryContainer != null && registryContainer.isRunning()) {
                    registryContainer.stop();
                }

            } catch (Exception e) {
                logger.warn("Error filling old registry with information: ", e);
            }
        }

        return Collections.emptyMap();
    }

    public void prepareData(String tenantManagerUrl, String registryBaseUrl) throws Exception {
        UpgradeTestsDataInitializer.prepareTestStorageUpgrade(SqlStorageUpgradeIT.class.getSimpleName(), tenantManagerUrl, "http://localhost:8081");

        //Wait until all the data is available for the upgrade test.
        TestUtils.retry(() -> Assertions.assertEquals(10, getTenantManagerClient(tenantManagerUrl).listTenants(TenantStatusValue.READY, 0, 51, SortOrder.asc, SortBy.tenantId).getCount()));

        MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);
        TenantUser tenantUser = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "storageUpgrade", UUID.randomUUID().toString());
        final TenantUserClient tenantUpgradeClient = mt.createTenant(tenantUser);

        SqlStorageUpgradeIT.upgradeTenantClient = tenantUpgradeClient.client;
    }

    private String startTenantManagerApplication(String tenantManagerImageName, String jdbcUrl, String username, String password) {
        tenantManagerContainer = new GenericContainer<>(tenantManagerImageName)
                .withEnv(Map.of("DATASOURCE_URL", jdbcUrl,
                        "REGISTRY_ROUTE_URL", "",
                        "DATASOURCE_USERNAME", username,
                        "DATASOURCE_PASSWORD", password,
                        "QUARKUS_HTTP_PORT", "8585"))
                .withNetworkMode("host");

        tenantManagerContainer.start();
        tenantManagerContainer.waitingFor(Wait.forLogMessage(".*Installed features:*", 1));

        return "http://localhost:8585";
    }

    @Override
    public void stop() {
        //Once the data is set, stop the old registry before running the tests.
        if (registryContainer != null && registryContainer.isRunning()) {
            registryContainer.stop();
        }

        if (tenantManagerContainer != null && tenantManagerContainer.isRunning()) {
            tenantManagerContainer.stop();
        }
    }

    private String startOldRegistryVersion(String imageName, String jdbcUrl, String username, String password, String tenantManagerUrl) {
        registryContainer = new GenericContainer<>(imageName)
                .withEnv(Map.of(
                        "REGISTRY_ENABLE_MULTITENANCY", "true",
                        "TENANT_MANAGER_AUTH_ENABLED", "false",
                        "TENANT_MANAGER_URL", tenantManagerUrl,
                        "REGISTRY_DATASOURCE_URL", jdbcUrl,
                        "REGISTRY_DATASOURCE_USERNAME", username,
                        "REGISTRY_DATASOURCE_PASSWORD", password,
                        "QUARKUS_HTTP_PORT", "8081"))
                .dependsOn(tenantManagerContainer)
                .withNetworkMode("host");

        registryContainer.start();
        //TODO change log message
        registryContainer.waitingFor(Wait.forLogMessage(".*Installed features:*", 1));

        return "http://localhost:8081";
    }

    public synchronized TenantManagerClient getTenantManagerClient(String tenantManagerUrl) {
        if (tenantManager == null) {
            tenantManager = new TenantManagerClientImpl(tenantManagerUrl, Collections.emptyMap(), null);
        }
        return tenantManager;
    }
}
