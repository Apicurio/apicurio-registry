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
 * Mixin interface providing common Apicurio Registry V3 wire format deserialization logic.
 * This can be used by any test class that needs to deserialize Avro messages encoded with
 * the V3 wire format: [magic byte (0x0)][int: content ID][avro payload]
 *
 * V3 format supports schema references which are resolved during deserialization.
 */
public interface DebeziumAvroV3DeserializerMixin {

    Logger LOG = LoggerFactory.getLogger(DebeziumAvroV3DeserializerMixin.class);

    /**
     * Provides access to the registry client for fetching schemas.
     * Must be implemented by the class using this mixin.
     */
    io.apicurio.registry.rest.client.RegistryClient getRegistryClient();

    /**
     * Deserializes Avro-encoded bytes to GenericRecord using V3 API format.
     * Handles Apicurio Registry V3 wire format (magic byte + contentId + payload)
     * and resolves schema references.
     */
    default GenericRecord deserializeAvroValueV3(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length < 5) {
            throw new IllegalArgumentException("Invalid Avro data: too short");
        }

        // Debug: Print first 20 bytes in hex
        StringBuilder hexDump = new StringBuilder("First bytes (hex): ");
        for (int i = 0; i < Math.min(20, bytes.length); i++) {
            hexDump.append(String.format("%02X ", bytes[i]));
        }
        LOG.info(hexDump.toString());

        // Parse Apicurio v3 wire format: [magic byte][int: content ID][payload]
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Skip magic byte (should be 0x0)
        byte magicByte = buffer.get();
        LOG.info("Magic byte: {} (0x{})", magicByte, String.format("%02X", magicByte));

        // Read content ID (4 bytes, big-endian int) - v3 converters use content IDs
        long contentId = buffer.getInt();
        LOG.info("Content ID from wire format (decimal): {}", contentId);
        LOG.info("Content ID from wire format (hex): 0x{}", Long.toHexString(contentId));

        // Fetch schema from registry using content ID, with references resolved
        try {
            // Fetch the schema content
            InputStream schemaStream = getRegistryClient().ids().contentIds().byContentId(contentId).get();
            String schemaJson = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);

            // Fetch references for this schema (v3 API)
            var references = getRegistryClient().ids().contentIds().byContentId(contentId).references().get();

            // Create a parser and add all referenced schemas first
            Schema.Parser parser = new Schema.Parser();

            if (references != null && !references.isEmpty()) {
                LOG.info("Schema has {} references, resolving...", references.size());
                for (var ref : references) {
                    try {
                        // Fetch each referenced schema using groupId, artifactId, version
                        String refGroupId = ref.getGroupId() != null ? ref.getGroupId() : "default";
                        String refArtifactId = ref.getArtifactId();
                        String refVersion = ref.getVersion();

                        LOG.info("Resolving reference: name={}, groupId={}, artifactId={}, version={}",
                                ref.getName(), refGroupId, refArtifactId, refVersion);

                        InputStream refStream;
                        if (refVersion != null && !refVersion.isEmpty()) {
                            refStream = getRegistryClient().groups().byGroupId(refGroupId)
                                    .artifacts().byArtifactId(refArtifactId)
                                    .versions().byVersionExpression(refVersion)
                                    .content().get();
                        } else {
                            refStream = getRegistryClient().groups().byGroupId(refGroupId)
                                    .artifacts().byArtifactId(refArtifactId)
                                    .versions().byVersionExpression("latest")
                                    .content().get();
                        }

                        String refSchemaJson = new String(refStream.readAllBytes(), StandardCharsets.UTF_8);
                        parser.parse(refSchemaJson);
                        LOG.info("Successfully resolved reference: {}", ref.getName());
                    } catch (Exception e) {
                        LOG.warn("Failed to resolve reference {}: {}", ref.getName(), e.getMessage());
                    }
                }
            }

            // Now parse the main schema with references resolved
            Schema schema = parser.parse(schemaJson);

            // Deserialize payload
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);

            return reader.read(null, decoder);
        } catch (Exception e) {
            LOG.error("Failed to fetch schema for contentId: {}. Error: {}", contentId, e.getMessage());
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
