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

import static io.apicurio.deployment.k8s.K8sClientManager.kubernetesClient;
import static io.apicurio.deployment.KubernetesTestResources.*;
import static io.apicurio.deployment.KubernetesTestResources.*;
import static io.apicurio.deployment.RegistryDeploymentManager.kubernetesClient;
import static io.apicurio.deployment.RegistryDeploymentManager.prepareTestsInfra;

public class SqlDeploymentManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlDeploymentManager.class);

    protected static void deploySqlApp(String registryImage) throws Exception {
        switch (Constants.TEST_PROFILE) {
            case Constants.AUTH:
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_SECURED_RESOURCES, true, registryImage, false);
                break;
            case Constants.MULTITENANCY:
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_MULTITENANT_RESOURCES, false, registryImage, true);
                break;
            case Constants.SQL:
                prepareSqlDbUpgradeTests(registryImage);
                break;
            default:
                prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_SQL_RESOURCES, false, registryImage, false);
                break;
        }
    }

    private static void prepareSqlDbUpgradeTests(String registryImage) throws Exception {
        LOGGER.info("Preparing data for SQL DB Upgrade migration tests...");

        //For the migration tests first we deploy the 2.1 version and add the required data.
        prepareTestsInfra(DATABASE_RESOURCES, APPLICATION_2_1_SQL_RESOURCES, false, null, true);
        prepareSqlMigrationData(ApicurioRegistryBaseIT.getTenantManagerUrl(), ApicurioRegistryBaseIT.getRegistryBaseUrl());

        //Once all the data has been introduced, the old deployment is deleted.
        deleteRegistryDeployment();

        //The Registry version 2.3 is deployed, the version introducing artifact references.
        prepareTestsInfra(null, APPLICATION_2_3_SQL_RESOURCES, false, null, false);
        prepareSqlReferencesMigrationData();

        //Once the references data is ready, we delete this old deployment and finally the current one is deployed.
        deleteRegistryDeployment();

        LOGGER.info("Finished preparing data for the SQL DB Upgrade tests.");
        prepareTestsInfra(null, APPLICATION_SQL_MULTITENANT_RESOURCES, false, registryImage, false);
    }

    private static void deleteRegistryDeployment() {
        final RollableScalableResource<Deployment> deploymentResource = kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT);

        kubernetesClient.apps().deployments().inNamespace(TEST_NAMESPACE).withName(APPLICATION_DEPLOYMENT).delete();

        //Wait for the deployment to be deleted
        CompletableFuture<List<Deployment>> deployment = deploymentResource
                .informOnCondition(Collection::isEmpty);

        try {
            deployment.get(60, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.warn("Error waiting for namespace deletion", e);
        } finally {
            deployment.cancel(true);
        }
    }

    private static void prepareSqlMigrationData(String tenantManagerUrl, String registryBaseUrl) throws Exception {
        final TenantManagerClientImpl tenantManagerClient = new TenantManagerClientImpl(tenantManagerUrl, Collections.emptyMap(), null);

        //Warm up until the tenant manager is ready.
        TestUtils.retry(() -> tenantManagerClient.listTenants(TenantStatusValue.READY, 0, 1, SortOrder.asc, SortBy.tenantId));

        LOGGER.info("Tenant manager is ready, filling registry with test data...");
        UpgradeTestsDataInitializer.prepareTestStorageUpgrade(SqlStorageUpgradeIT.class.getSimpleName(), tenantManagerUrl, registryBaseUrl);
        TestUtils.waitFor("Waiting for tenant data to be available...", 3000, 180000, () -> tenantManagerClient.listTenants(TenantStatusValue.READY, 0, 51, SortOrder.asc, SortBy.tenantId).getCount() == 10);
        LOGGER.info("Done filling registry with test data...");

        MultitenancySupport mt = new MultitenancySupport(tenantManagerUrl, registryBaseUrl);
        TenantUser tenantUser = new TenantUser(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "storageUpgrade", UUID.randomUUID().toString());
        final TenantUserClient tenantUpgradeClient = mt.createTenant(tenantUser);

        //Prepare the data for the content and canonical hash upgraders using an isolated tenant so we don't have data conflicts.
        UpgradeTestsDataInitializer.prepareProtobufHashUpgradeTest(tenantUpgradeClient.client);
        UpgradeTestsDataInitializer.prepareAvroCanonicalHashUpgradeData(tenantUpgradeClient.client);

        SqlStorageUpgradeIT.upgradeTenantClient = tenantUpgradeClient.client;
        SqlProtobufCanonicalHashUpgraderIT.upgradeTenantClient = tenantUpgradeClient.client;
        SqlAvroUpgraderIT.upgradeTenantClient = tenantUpgradeClient.client;
        SqlReferencesUpgraderIT.upgradeTenantClient = tenantUpgradeClient.client;
    }

    private static void prepareSqlReferencesMigrationData() throws Exception {
        UpgradeTestsDataInitializer.prepareReferencesUpgradeTest(SqlReferencesUpgraderIT.upgradeTenantClient);
    }
}
