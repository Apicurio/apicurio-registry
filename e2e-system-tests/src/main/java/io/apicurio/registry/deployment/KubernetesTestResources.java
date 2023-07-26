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

package io.apicurio.registry.deployment;

public class KubernetesTestResources {

    protected static final String E2E_NAMESPACE_RESOURCE = "/e2e-namespace.yml";

    protected static final String APPLICATION_IN_MEMORY_RESOURCES = "/in-memory/registry-in-memory.yml";
    protected static final String APPLICATION_SQL_RESOURCES = "/sql/registry-sql.yml";
    protected static final String APPLICATION_KAFKA_RESOURCES = "/kafka/registry-kafka.yml";

    protected static final String APPLICATION_IN_MEMORY_SECURED_RESOURCES = "/in-memory/registry-in-memory-secured.yml";
    protected static final String APPLICATION_SQL_SECURED_RESOURCES = "/sql/registry-sql-secured.yml";
    protected static final String APPLICATION_KAFKA_SECURED_RESOURCES = "/kafka/registry-kafka-secured.yml";

    protected static final String KAFKA_RESOURCES = "/kafka/kafka.yml";
    protected static final String DATABASE_RESOURCES = "/sql/postgresql.yml";
    protected static final String KEYCLOAK_RESOURCES = "/auth/keycloak.yml";

    protected static final String TEST_NAMESPACE = "apicurio-registry-e2e"; //TODO try to use @KubernetesTest with the dynamic namespace

    protected static final String APPLICATION_SERVICE = "apicurio-registry-service";
    protected static final String KEYCLOAK_SERVICE = "keycloak-service";


}
