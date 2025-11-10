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
 * serialization using PUBLISHED converters from Maven Central.
 *
 * Tests schema auto-registration, evolution, MySQL data types, and CDC operations.
 */
@Tag(Constants.DEBEZIUM_MYSQL)
@QuarkusIntegrationTest
@QuarkusTestResource(value = DebeziumMySQLContainerResource.class, restrictToAnnotatedClass = true)
public class DebeziumMySQLAvroIntegrationIT extends DebeziumMySQLAvroBaseIT
        implements DebeziumAvroV2DeserializerMixin {

    @Override
    protected String getRegistryUrl() {
        return getRegistryV2ApiUrl();
    }

    @Override
    protected DebeziumContainer getDebeziumContainer() {
        return DebeziumMySQLContainerResource.debeziumContainer;
    }

    @Override
    protected MySQLContainer<?> getMySQLContainer() {
        return DebeziumMySQLContainerResource.mysqlContainer;
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
        return deserializeAvroValueV2(bytes);
    }
}
