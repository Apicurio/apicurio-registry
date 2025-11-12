package io.apicurio.tests.serdes.apicurio.debezium;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Mixin interface providing common Apicurio Registry V2 wire format deserialization logic.
 * This can be used by any test class that needs to deserialize Avro messages encoded with
 * the V2 wire format: [magic byte (0x0)][long: global ID][avro payload]
 */
public interface DebeziumAvroV2DeserializerMixin {

    Logger LOG = LoggerFactory.getLogger(DebeziumAvroV2DeserializerMixin.class);

    /**
     * Provides access to the registry client for fetching schemas.
     * Must be implemented by the class using this mixin.
     */
    io.apicurio.registry.rest.client.RegistryClient getRegistryClient();

    /**
     * Deserializes Avro-encoded bytes to GenericRecord using V2 API format.
     * Handles Apicurio Registry V2 wire format (magic byte + globalId + payload)
     */
    default GenericRecord deserializeAvroValueV2(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length < 5) {
            throw new IllegalArgumentException("Invalid Avro data: too short");
        }

        // Debug: Print first 20 bytes in hex
        StringBuilder hexDump = new StringBuilder("First bytes (hex): ");
        for (int i = 0; i < Math.min(20, bytes.length); i++) {
            hexDump.append(String.format("%02X ", bytes[i]));
        }
        LOG.info(hexDump.toString());

        // Parse Apicurio V2 wire format: [magic byte][long: global ID][payload]
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Skip magic byte (should be 0x0)
        byte magicByte = buffer.get();
        LOG.info("Magic byte: {} (0x{})", magicByte, String.format("%02X", magicByte));

        // Read global ID (8 bytes, big-endian long)
        long globalId = buffer.getLong();
        LOG.info("Global ID from wire format (decimal): {}", globalId);
        LOG.info("Global ID from wire format (hex): 0x{}", Long.toHexString(globalId));

        // Fetch schema from registry using global ID
        try {
            InputStream schemaStream = getRegistryClient().ids().globalIds().byGlobalId(globalId).get();
            String schemaJson = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
            Schema schema = new Schema.Parser().parse(schemaJson);

            // Deserialize payload
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);

            return reader.read(null, decoder);
        } catch (Exception e) {
            LOG.error("Failed to fetch schema for globalId: {}. Error: {}", globalId, e.getMessage());
            LOG.error("Trying to list recent artifacts to debug...");
            try {
                var artifacts = getRegistryClient().search().artifacts().get(config -> {
                    config.queryParameters.limit = 10;
                    config.queryParameters.orderby = io.apicurio.registry.rest.client.models.ArtifactSortBy.CreatedOn;
                    config.queryParameters.order = io.apicurio.registry.rest.client.models.SortOrder.Desc;
                });
                LOG.error("Recent artifacts in registry:");
                artifacts.getArtifacts().forEach(artifact -> {
                    LOG.error("  - Artifact: {}, Group: {}, Type: {}",
                            artifact.getArtifactId(), artifact.getGroupId(), artifact.getArtifactType());
                });
            } catch (Exception listError) {
                LOG.error("Could not list artifacts", listError);
            }
            throw e;
        }
    }
}
