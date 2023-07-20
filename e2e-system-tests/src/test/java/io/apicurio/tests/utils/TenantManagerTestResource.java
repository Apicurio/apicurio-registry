/*
 * Copyright 2023 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.tests.utils;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tenantmanager.api.datamodel.SortBy;
import io.apicurio.tenantmanager.api.datamodel.SortOrder;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.client.TenantManagerClient;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import io.apicurio.tests.dbupgrade.SqlStorageUpgradeIT;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;

public class TenantManagerTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger logger = LoggerFactory.getLogger(SqlStorageUpgradeIT.class);

    GenericContainer tenantManagerContainer;
    EmbeddedPostgres database;
    TenantManagerClient tenantManager;

    @Override
    public int order() {
        return 10000;
    }

    @Override
    public Map<String, String> start() {
        if (!Boolean.parseBoolean(System.getProperty("cluster.tests"))) {
            try {
                database = EmbeddedPostgres.builder().setPort(5431).start();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            String datasourceUrl = database.getJdbcUrl("postgres", "postgres");

            String tenantManagerUrl = startTenantManagerApplication("quay.io/apicurio/apicurio-tenant-manager-api:latest", datasourceUrl, "postgres", "postgres");

            try {
                //Warm up until the tenant manager is ready.
                TestUtils.retry(() -> {
                    getTenantManagerClient(tenantManagerUrl).listTenants(TenantStatusValue.READY, 0, 1, SortOrder.asc, SortBy.tenantId);
                });

            } catch (Exception ex) {
                logger.warn("Error filling old registry with information: ", ex);
            }
        }

        return Collections.emptyMap();
    }

    private String startTenantManagerApplication(String tenantManagerImageName, String jdbcUrl, String username, String password) {
        tenantManagerContainer = new GenericContainer<>(tenantManagerImageName)
                .withEnv(Map.of("DATASOURCE_URL", jdbcUrl,
                        "REGISTRY_ROUTE_URL", "",
                        "DATASOURCE_USERNAME", username,
                        "DATASOURCE_PASSWORD", password,
                        "QUARKUS_HTTP_PORT", "8585",
                        "ENABLE_TEST_STATUS_TRANSITION", "true"))
                .withNetworkMode("host");

        tenantManagerContainer.start();
        tenantManagerContainer.waitingFor(Wait.forLogMessage(".*Installed features:*", 1));

        return "http://localhost:8585";
    }

    @Override
    public void stop() {
        if (tenantManagerContainer != null && tenantManagerContainer.isRunning()) {
            tenantManagerContainer.stop();
        }
    }

    public synchronized TenantManagerClient getTenantManagerClient(String tenantManagerUrl) {
        if (tenantManager == null) {
            tenantManager = new TenantManagerClientImpl(tenantManagerUrl, Collections.emptyMap(), null);
        }
        return tenantManager;
    }
}
