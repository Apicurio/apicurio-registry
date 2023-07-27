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

public class KubernetesTestResources {

    protected static final String E2E_NAMESPACE_RESOURCE = "/infra/e2e-namespace.yml";
    protected static final String REGISTRY_OPENSHIFT_ROUTE = "/infra/openshift/registry-route.yml";

    protected static final String TENANT_MANAGER_OPENSHIFT_ROUTE = "/infra/openshift/tenant-manager-route.yml";

    protected static final String APPLICATION_IN_MEMORY_RESOURCES = "/infra/in-memory/registry-in-memory.yml";
    protected static final String APPLICATION_SQL_RESOURCES = "/infra/sql/registry-sql.yml";
    protected static final String APPLICATION_KAFKA_RESOURCES = "/infra/kafka/registry-kafka.yml";

    protected static final String APPLICATION_IN_MEMORY_SECURED_RESOURCES = "/infra/in-memory/registry-in-memory-secured.yml";
    protected static final String APPLICATION_SQL_SECURED_RESOURCES = "/infra/sql/registry-sql-secured.yml";
    protected static final String APPLICATION_KAFKA_SECURED_RESOURCES = "/infra/kafka/registry-kafka-secured.yml";

    protected static final String APPLICATION_IN_MEMORY_MULTITENANT_RESOURCES = "/infra/in-memory/registry-multitenant-in-memory.yml";
    protected static final String APPLICATION_SQL_MULTITENANT_RESOURCES = "/infra/sql/registry-multitenant-sql.yml";
    protected static final String APPLICATION_KAFKA_MULTITENANT_RESOURCES = "/infra/kafka/registry-multitenant-kafka.yml";

    protected static final String APPLICATION_OLD_SQL_RESOURCES = "/infra/sql/registry-sql-old.yml";
    protected static final String APPLICATION_OLD_KAFKA_RESOURCES = "/infra/kafka/registry-kafka-old.yml";

    protected static final String TENANT_MANAGER_RESOURCES = "/infra/tenant-manager/tenant-manager.yml";
    protected static final String TENANT_MANAGER_DATABASE = "/infra/tenant-manager/tenant-manager-database.yml";

    protected static final String KAFKA_RESOURCES = "/infra/kafka/kafka.yml";
    protected static final String DATABASE_RESOURCES = "/infra/sql/postgresql.yml";
    protected static final String KEYCLOAK_RESOURCES = "/infra/auth/keycloak.yml";

    protected static final String TEST_NAMESPACE = "apicurio-registry-e2e";

    protected static final String APPLICATION_SERVICE = "apicurio-registry-service";
    protected static final String APPLICATION_DEPLOYMENT = "apicurio-registry-deployment";
    protected static final String KEYCLOAK_SERVICE = "keycloak-service";
    protected static final String TENANT_MANAGER_SERVICE = "tenant-manager-service";
}
