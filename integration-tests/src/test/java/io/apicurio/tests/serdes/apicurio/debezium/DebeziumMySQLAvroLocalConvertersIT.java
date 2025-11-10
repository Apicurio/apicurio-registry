package io.apicurio.tests.serdes.apicurio.debezium;

import io.apicurio.tests.utils.Constants;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.MySQLContainer;

/**
 * Integration tests for Debezium MySQL CDC with Apicurio Registry Avro
 * serialization using LOCALLY BUILT converters. This test validates integration with the
 * current SNAPSHOT version of the converter library rather than published versions from Maven
 * Central.
 *
 * Tests schema auto-registration, evolution, MySQL data types, and CDC operations.
 */
@Tag(Constants.DEBEZIUM_SNAPSHOT)
@QuarkusIntegrationTest
@QuarkusTestResource(value = DebeziumMySQLLocalConvertersResource.class, restrictToAnnotatedClass = true)
public class DebeziumMySQLAvroLocalConvertersIT extends DebeziumMySQLAvroBaseIT
        implements DebeziumAvroV3DeserializerMixin {

    @Override
    protected String getRegistryUrl() {
        return getRegistryV3ApiUrl();
    }

    @Override
    protected DebeziumContainer getDebeziumContainer() {
        return DebeziumMySQLLocalConvertersResource.debeziumContainer;
    }

    @Override
    protected MySQLContainer<?> getMySQLContainer() {
        return DebeziumMySQLLocalConvertersResource.mysqlContainer;
    }

    @Override
    public io.apicurio.registry.rest.client.RegistryClient getRegistryClient() {
        return registryClient;
    }

    /**
     * Deserializes Avro-encoded bytes to GenericRecord using V2 API format.
     * Delegates to the mixin implementation.
     */
    @Override
    protected GenericRecord deserializeAvroValue(byte[] bytes) throws Exception {
        return deserializeAvroValueV3(bytes);
    }
}
