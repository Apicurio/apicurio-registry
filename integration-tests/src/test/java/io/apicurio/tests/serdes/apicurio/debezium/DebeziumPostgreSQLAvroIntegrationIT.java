package io.apicurio.tests.serdes.apicurio.debezium;

import io.apicurio.tests.utils.Constants;
import io.debezium.testing.testcontainers.DebeziumContainer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Integration tests for Debezium PostgreSQL CDC with Apicurio Registry Avro
 * serialization using PUBLISHED converters from Maven Central.
 *
 * Tests schema auto-registration, evolution, PostgreSQL data types, and CDC
 * operations.
 */
@Tag(Constants.DEBEZIUM)
@QuarkusIntegrationTest
@QuarkusTestResource(value = DebeziumContainerResource.class, restrictToAnnotatedClass = true)
public class DebeziumPostgreSQLAvroIntegrationIT extends DebeziumPostgreSQLAvroBaseIT {

    private static final Logger log = LoggerFactory.getLogger(DebeziumPostgreSQLAvroIntegrationIT.class);

    @Override
    protected String getRegistryUrl() {
        return getRegistryV2ApiUrl();
    }

    @Override
    protected DebeziumContainer getDebeziumContainer() {
        return DebeziumContainerResource.debeziumContainer;
    }

    @Override
    protected PostgreSQLContainer<?> getPostgresContainer() {
        return DebeziumContainerResource.postgresContainer;
    }

    /**
     * Deserializes Avro-encoded bytes to GenericRecord using V2 API format.
     * Handles Apicurio Registry V2 wire format (magic byte + globalId + payload)
     */
    @Override
    protected GenericRecord deserializeAvroValue(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length < 5) {
            throw new IllegalArgumentException("Invalid Avro data: too short");
        }

        // Debug: Print first 20 bytes in hex
        StringBuilder hexDump = new StringBuilder("First bytes (hex): ");
        for (int i = 0; i < Math.min(20, bytes.length); i++) {
            hexDump.append(String.format("%02X ", bytes[i]));
        }
        log.info(hexDump.toString());

        // Parse Apicurio V2 wire format: [magic byte][long: global ID][payload]
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Skip magic byte (should be 0x0)
        byte magicByte = buffer.get();
        log.info("Magic byte: {} (0x{})", magicByte, String.format("%02X", magicByte));

        // Read global ID (8 bytes, big-endian long)
        long globalId = buffer.getLong();
        log.info("Global ID from wire format (decimal): {}", globalId);
        log.info("Global ID from wire format (hex): 0x{}", Long.toHexString(globalId));

        // Fetch schema from registry using global ID
        try {
            InputStream schemaStream = registryClient.ids().globalIds().byGlobalId(globalId).get();
            String schemaJson = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
            Schema schema = new Schema.Parser().parse(schemaJson);

            // Deserialize payload
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);

            return reader.read(null, decoder);
        } catch (Exception e) {
            log.error("Failed to fetch schema for globalId: {}. Error: {}", globalId, e.getMessage());
            log.error("Trying to list recent artifacts to debug...");
            try {
                var artifacts = registryClient.search().artifacts().get(config -> {
                    config.queryParameters.limit = 10;
                    config.queryParameters.orderby = io.apicurio.registry.rest.client.models.ArtifactSortBy.CreatedOn;
                    config.queryParameters.order = io.apicurio.registry.rest.client.models.SortOrder.Desc;
                });
                log.error("Recent artifacts in registry:");
                artifacts.getArtifacts().forEach(artifact -> {
                    log.error("  - Artifact: {}, Group: {}, Type: {}",
                            artifact.getArtifactId(), artifact.getGroupId(), artifact.getArtifactType());
                });
            } catch (Exception listError) {
                log.error("Could not list artifacts", listError);
            }
            throw e;
        }
    }
}
