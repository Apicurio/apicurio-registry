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

package io.apicurio.deployment;

import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tenantmanager.api.datamodel.SortBy;
import io.apicurio.tenantmanager.api.datamodel.SortOrder;
import io.apicurio.tenantmanager.api.datamodel.TenantStatusValue;
import io.apicurio.tenantmanager.client.TenantManagerClientImpl;
import io.apicurio.tests.ApicurioRegistryBaseIT;
import io.apicurio.tests.dbupgrade.sql.SqlAvroUpgraderIT;
import io.apicurio.tests.dbupgrade.sql.SqlProtobufCanonicalHashUpgraderIT;
import io.apicurio.tests.dbupgrade.sql.SqlReferencesUpgraderIT;
import io.apicurio.tests.dbupgrade.sql.SqlStorageUpgradeIT;
import io.apicurio.tests.dbupgrade.UpgradeTestsDataInitializer;
import io.apicurio.tests.multitenancy.MultitenancySupport;
import io.apicurio.tests.multitenancy.TenantUser;
import io.apicurio.tests.multitenancy.TenantUserClient;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.apicurio.deployment.KubernetesTestResources.*;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;
import static io.apicurio.deployment.k8s.K8sClientManager.kubernetesClient;

public class SqlDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlDeploymentManager.class);

    protected static void deploySqlApp(String registryImage) throws Exception {
        LOGGER.info("Deploying SQL-based Registry variant with test profile: {}", Constants.TEST_PROFILE);
        
        switch (Constants.TEST_PROFILE) {
            case Constants.AUTH:
                LOGGER.info("Configuring SQL Registry with authentication (Keycloak) enabled");
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_SECURED_RESOURCES, true, registryImage, false);
                break;
            case Constants.MULTITENANCY:
                LOGGER.info("Configuring SQL Registry with multitenancy support enabled");
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_MULTITENANT_RESOURCES, false, registryImage, true);
                break;
            case Constants.SQL:
                LOGGER.info("Configuring SQL Registry for database upgrade testing");
                prepareSqlDbUpgradeTests(registryImage);
                break;
            default:
                LOGGER.info("Configuring standard SQL Registry deployment");
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_RESOURCES, false, registryImage, false);
                break;
        }
        LOGGER.info("SQL Registry variant deployment completed successfully");
    }

    private static void prepareSqlDbUpgradeTests(String registryImage) throws Exception {
        LOGGER.info("Preparing data for SQL DB Upgrade migration tests ##################################################");

        //For the migration tests first we deploy the 2.1 version and add the required data.
        LOGGER.info("Phase 1: Deploying Registry v2.1 with SQL database for initial data setup");
        prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_2_1_SQL_RESOURCES, false, null, true);
        LOGGER.info("Phase 1: Populating v2.1 Registry with migration test data");
        prepareSqlMigrationData(ApicurioRegistryBaseIT.getTenantManagerUrl(), ApicurioRegistryBaseIT.getRegistryBaseUrl());

        //Once all the data has been introduced, the old deployment is deleted.
        LOGGER.info("Phase 1: Removing v2.1 Registry deployment to prepare for v2.4 upgrade");
        deleteRegistryDeployment();

        //The Registry version 2.3 is deployed, the version introducing artifact references.
        LOGGER.info("Phase 2: Deploying Registry v2.4 for references migration testing");
        prepareTestsInfra(null, APPLICATION_2_4_SQL_RESOURCES, false, null, false);
        LOGGER.info("Phase 2: Adding artifact references test data to v2.4 Registry");
        prepareSqlReferencesMigrationData();

        //Once the references data is ready, we delete this old deployment and finally the current one is deployed.
        LOGGER.info("Phase 2: Removing v2.4 Registry deployment to prepare for current version");
        deleteRegistryDeployment();

        LOGGER.info("Phase 3: Deploying current Registry version for upgrade validation");
        prepareTestsInfra(null, APPLICATION_SQL_MULTITENANT_RESOURCES, false, registryImage, false);
        LOGGER.info("Finished preparing data for the SQL DB Upgrade tests ##################################################");
    }

    private static void deleteRegistryDeployment() {
        LOGGER.info("Deleting Registry deployment: {} from namespace: {}", APPLICATION_DEPLOYMENT, TEST_NAMESPACE);
        
        final RollableScalableResource<Deployment> deploymentResource = kubernetesClient().apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT);

        kubernetesClient().apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT).delete();
        LOGGER.info("Registry deployment deletion initiated, waiting for completion (timeout: 60 seconds)...");

        //Wait for the deployment to be deleted
        CompletableFuture<List<Deployment>> deployment = deploymentResource
                .informOnCondition(Collection::isEmpty);

        try {
            deployment.get(60, TimeUnit.SECONDS);
            LOGGER.info("Registry deployment {} successfully deleted", APPLICATION_DEPLOYMENT);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Error waiting for deployment {} deletion", APPLICATION_DEPLOYMENT, e);
        } finally {
            deployment.cancel(true);
        }
    }

    private static void prepareSqlMigrationData(String tenantManagerUrl, String registryBaseUrl) throws Exception {
        LOGGER.info("Initializing migration test data for SQL Registry v2.1");
        LOGGER.info("  -> Tenant Manager URL: {}", tenantManagerUrl);
        LOGGER.info("  -> Registry Base URL: {}", registryBaseUrl);
        
        final TenantManagerClientImpl tenantManagerClient = new TenantManagerClientImpl(tenantManagerUrl, Collections.emptyMap(), null);

        //Warm up until the tenant manager is ready.
        LOGGER.info("  -> Waiting for Tenant Manager to be ready...");
        TestUtils.retry(() -> tenantManagerClient.listTenants(TenantStatusValue.READY, 0, 1, SortOrder.asc, SortBy.tenantId));

        LOGGER.info("  -> Tenant manager is ready, populating registry with storage upgrade test data...");
        UpgradeTestsDataInitializer.prepareTestStorageUpgrade(SqlStorageUpgradeIT.class.getSimpleName(), tenantManagerUrl, registryBaseUrl);
        TestUtils.waitFor("Waiting for tenant data to be available...", 3000, 180000, () -> tenantManagerClient.listTenants(TenantStatusValue.READY, 0, 51, SortOrder.asc, SortBy.tenantId).getCount() == 10);
        LOGGER.info("  -> Storage upgrade test data populated successfully");

        LOGGER.info("  -> Creating isolated tenant for upgrade tests...");
        MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);
        TenantUser tenantUser = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "storageUpgrade", UUID.randomUUID().toString());
        final TenantUserClient tenantUpgradeClient = mt.createTenant(tenantUser);
        LOGGER.info("  -> Isolated upgrade tenant created: {}", tenantUser.tenantId);

        //Prepare the data for the content and canonical hash upgraders using an isolated tenant so we don't have data conflicts.
        LOGGER.info("  -> Preparing Protobuf hash upgrade test data...");
        UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(tenantUpgradeClient.client);
        LOGGER.info("  -> Preparing Avro canonical hash upgrade test data...");
        UpgradeTestsDataInitializer.prepareAvroCanonicalHashUpgradeData(tenantUpgradeClient.client);

        SqlStorageUpgradeIT.upgradeTenantClient = tenantUpgradeClient.client;
        SqlProtobufCanonicalHashUpgraderIT.upgradeTenantClient = tenantUpgradeClient.client;
        SqlAvroUpgraderIT.upgradeTenantClient = tenantUpgradeClient.client;
        SqlReferencesUpgraderIT.upgradeTenantClient = tenantUpgradeClient.client;
        
        LOGGER.info("SQL migration test data preparation completed");
    }

    private static void prepareSqlReferencesMigrationData() throws Exception {
        LOGGER.info("  -> Preparing artifact references migration test data for SQL Registry v2.4...");
        UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(SqlReferencesUpgraderIT.upgradeTenantClient);
        LOGGER.info("  -> Artifact references migration test data preparation completed");
    }
}
