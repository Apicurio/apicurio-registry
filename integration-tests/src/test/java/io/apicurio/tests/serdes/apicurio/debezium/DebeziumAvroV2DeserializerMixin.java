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
import java.util.List;

/**
 * Mixin interface providing Apicurio Registry wire format deserialization logic with
 * optimistic fallback between 4-byte and 8-byte ID formats.
 *
 * <p>Applies the same heuristic as {@code OptimisticFallbackIdHandler}: peek at the first
 * 4 bytes after the magic byte. If they are all zeros the data was written with the legacy
 * 8-byte format (globalId as a long); otherwise it was written with the default 4-byte
 * format (contentId as an int). The schema is then fetched via the matching endpoint and
 * any Avro references are resolved before deserialization.
 */
public interface DebeziumAvroV2DeserializerMixin {

    Logger LOG = LoggerFactory.getLogger(DebeziumAvroV2DeserializerMixin.class);

    /**
     * Provides access to the registry client for fetching schemas.
     * Must be implemented by the class using this mixin.
     */
    io.apicurio.registry.rest.client.RegistryClient getRegistryClient();

    /**
     * Deserializes Avro-encoded bytes to GenericRecord, auto-detecting the wire format.
     *
     * <p>If the first 4 bytes after the magic byte are all zeros the record is treated as
     * legacy 8-byte globalId format; otherwise it is treated as default 4-byte contentId
     * format. This mirrors the {@code OptimisticFallbackIdHandler} logic used in the serde
     * library. Schema references are resolved before parsing.
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

        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // Skip magic byte (should be 0x0)
        byte magicByte = buffer.get();
        LOG.info("Magic byte: {} (0x{})", magicByte, String.format("%02X", magicByte));

        // Optimistic fallback: peek at the first 4 bytes to decide the format.
        // If all four are zero and there is room for another 4 bytes, this was
        // written with Legacy8ByteIdHandler (8-byte globalId). Otherwise, it was
        // written with Default4ByteIdHandler (4-byte contentId).
        int firstFour = buffer.getInt();

        boolean legacy8Byte = (firstFour == 0 && buffer.remaining() >= 4);

        String schemaJson;
        Schema.Parser parser = new Schema.Parser();

        if (legacy8Byte) {
            // Rewind the 4 bytes we just read and consume a full 8-byte long
            buffer.position(buffer.position() - 4);
            long globalId = buffer.getLong();
            LOG.info("Detected legacy 8-byte format. Global ID: {} (0x{})",
                    globalId, Long.toHexString(globalId));
            schemaJson = fetchSchemaStringByGlobalId(globalId);
            resolveReferencesByGlobalId(globalId, parser);
        } else {
            long contentId = Integer.toUnsignedLong(firstFour);
            LOG.info("Detected default 4-byte format. Content ID: {} (0x{})",
                    contentId, Long.toHexString(contentId));
            schemaJson = fetchSchemaStringByContentId(contentId);
            resolveReferencesByContentId(contentId, parser);
        }

        try {
            Schema schema = parser.parse(schemaJson);

            // Deserialize payload
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            GenericDatumReader<GenericRecord> reader = new GenericDatumReader<>(schema);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, null);

            return reader.read(null, decoder);
        } catch (Exception e) {
            LOG.error("Failed to deserialize Avro record. Error: {}", e.getMessage());
            logRecentArtifacts();
            throw e;
        }
    }

    /**
     * Fetches a schema as a JSON string by globalId (legacy 8-byte format).
     */
    private String fetchSchemaStringByGlobalId(long globalId) throws Exception {
        try {
            InputStream stream = getRegistryClient().ids().globalIds().byGlobalId(globalId).get();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("Failed to fetch schema for globalId: {}. Error: {}", globalId, e.getMessage());
            logRecentArtifacts();
            throw e;
        }
    }

    /**
     * Fetches a schema as a JSON string by contentId (default 4-byte format).
     */
    private String fetchSchemaStringByContentId(long contentId) throws Exception {
        try {
            InputStream stream = getRegistryClient().ids().contentIds().byContentId(contentId).get();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("Failed to fetch schema for contentId: {}. Error: {}", contentId, e.getMessage());
            logRecentArtifacts();
            throw e;
        }
    }

    /**
     * Resolves and pre-parses schema references for a globalId.
     */
    private void resolveReferencesByGlobalId(long globalId, Schema.Parser parser) {
        try {
            var references = getRegistryClient().ids().globalIds().byGlobalId(globalId).references().get();
            resolveReferences(references, parser);
        } catch (Exception e) {
            LOG.warn("Could not resolve references for globalId {}: {}", globalId, e.getMessage());
        }
    }

    /**
     * Resolves and pre-parses schema references for a contentId.
     */
    private void resolveReferencesByContentId(long contentId, Schema.Parser parser) {
        try {
            var references = getRegistryClient().ids().contentIds().byContentId(contentId).references().get();
            resolveReferences(references, parser);
        } catch (Exception e) {
            LOG.warn("Could not resolve references for contentId {}: {}", contentId, e.getMessage());
        }
    }

    /**
     * Fetches and pre-parses each referenced schema so the main schema can resolve them.
     */
    private void resolveReferences(
            List<io.apicurio.registry.rest.client.models.ArtifactReference> references,
            Schema.Parser parser) {
        if (references == null || references.isEmpty()) {
            return;
        }
        LOG.info("Schema has {} references, resolving...", references.size());
        for (var ref : references) {
            try {
                String refGroupId = ref.getGroupId() != null ? ref.getGroupId() : "default";
                String refArtifactId = ref.getArtifactId();
                String refVersion = ref.getVersion();

                LOG.info("Resolving reference: name={}, groupId={}, artifactId={}, version={}",
                        ref.getName(), refGroupId, refArtifactId, refVersion);

                String versionExpr = (refVersion != null && !refVersion.isEmpty()) ? refVersion : "latest";
                InputStream refStream = getRegistryClient().groups().byGroupId(refGroupId)
                        .artifacts().byArtifactId(refArtifactId)
                        .versions().byVersionExpression(versionExpr)
                        .content().get();

                String refSchemaJson = new String(refStream.readAllBytes(), StandardCharsets.UTF_8);
                parser.parse(refSchemaJson);
                LOG.info("Successfully resolved reference: {}", ref.getName());
            } catch (Exception e) {
                LOG.warn("Failed to resolve reference {}: {}", ref.getName(), e.getMessage());
            }
        }
    }

    /**
     * Logs recently registered artifacts for debugging purposes.
     */
    private void logRecentArtifacts() {
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
    }
}
