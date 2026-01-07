package io.apicurio.registry.serde.kafka.headers;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.serde.config.IdOption;
import io.apicurio.registry.utils.IoUtil;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.nio.ByteBuffer;
import java.util.Map;

public class DefaultHeadersHandler implements HeadersHandler {

    private String globalIdHeaderName;
    private String contentIdHeaderName;
    private String contentHashHeaderName;
    private String groupIdHeaderName;
    private String artifactIdHeaderName;
    private String versionHeaderName;
    private IdOption idOption;
    private boolean useSchemaFromHeaders;
    private String schemaHeaderName;
    private String schemaTypeHeaderName;

    /**
     * @see io.apicurio.registry.serde.kafka.headers.HeadersHandler#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, Object> configs, boolean isKey) {
        DefaultHeadersHandlerConfig config = new DefaultHeadersHandlerConfig(configs);
        if (isKey) {
            globalIdHeaderName = config.getKeyGlobalIdHeader();
            contentIdHeaderName = config.getKeyContentIdHeader();
            contentHashHeaderName = config.getKeyContentHashHeader();
            groupIdHeaderName = config.getKeyGroupIdHeader();
            artifactIdHeaderName = config.getKeyArtifactIdHeader();
            versionHeaderName = config.getKeyVersionHeader();
            schemaHeaderName = config.getKeySchemaHeader();
            schemaTypeHeaderName = config.getKeySchemaTypeHeader();
        } else {
            globalIdHeaderName = config.getValueGlobalIdHeader();
            contentIdHeaderName = config.getValueContentIdHeader();
            contentHashHeaderName = config.getValueContentHashHeader();
            groupIdHeaderName = config.getValueGroupIdHeader();
            artifactIdHeaderName = config.getValueArtifactIdHeader();
            versionHeaderName = config.getValueVersionHeader();
            schemaHeaderName = config.getValueSchemaHeader();
            schemaTypeHeaderName = config.getValueSchemaTypeHeader();
        }
        if (config.useIdOption() != null) {
            idOption = config.useIdOption();
        }
        useSchemaFromHeaders = config.useSchemaFromHeaders();
    }

    /**
     * @see io.apicurio.registry.serde.kafka.headers.HeadersHandler#writeHeaders(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.resolver.strategy.ArtifactReference)
     */
    @Override
    public void writeHeaders(Headers headers, ArtifactReference reference) {
        if (idOption == IdOption.globalId) {
            if (reference.getGlobalId() == null) {
                throw new SerializationException(
                        "Missing globalId. IdOption is globalId but there is no contentId in the ArtifactReference");
            }
            ByteBuffer buff = ByteBuffer.allocate(8);
            buff.putLong(reference.getGlobalId());
            headers.add(globalIdHeaderName, buff.array());
            return;
        }

        if (reference.getContentId() != null) {
            ByteBuffer buff = ByteBuffer.allocate(8);
            buff.putLong(reference.getContentId());
            headers.add(contentIdHeaderName, buff.array());
        } else {
            if (reference.getContentHash() != null) {
                headers.add(contentHashHeaderName, IoUtil.toBytes(reference.getContentHash()));
            }
            headers.add(groupIdHeaderName, IoUtil.toBytes(reference.getGroupId()));
            headers.add(artifactIdHeaderName, IoUtil.toBytes(reference.getArtifactId()));
            if (reference.getVersion() != null) {
                headers.add(versionHeaderName, IoUtil.toBytes(reference.getVersion()));
            }
        }
    }

    /**
     * @see io.apicurio.registry.serde.kafka.headers.HeadersHandler#readHeaders(org.apache.kafka.common.header.Headers)
     */
    @Override
    public ArtifactReference readHeaders(Headers headers) {
        return ArtifactReference.builder().globalId(getGlobalId(headers)).contentId(getContentId(headers))
                .contentHash(getContentHash(headers)).groupId(getGroupId(headers))
                .artifactId(getArtifactId(headers)).version(getVersion(headers)).build();
    }

    private String getGroupId(Headers headers) {
        Header header = headers.lastHeader(groupIdHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    private String getArtifactId(Headers headers) {
        Header header = headers.lastHeader(artifactIdHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    private String getVersion(Headers headers) {
        Header header = headers.lastHeader(versionHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    private Long getGlobalId(Headers headers) {
        Header header = headers.lastHeader(globalIdHeaderName);
        if (header == null) {
            return null;
        } else {
            return ByteBuffer.wrap(header.value()).getLong();
        }
    }

    private Long getContentId(Headers headers) {
        Header header = headers.lastHeader(contentIdHeaderName);
        if (header == null) {
            return null;
        } else {
            return ByteBuffer.wrap(header.value()).getLong();
        }
    }

    private String getContentHash(Headers headers) {
        Header header = headers.lastHeader(contentHashHeaderName);
        if (header == null) {
            return null;
        } else {
            return IoUtil.toString(header.value());
        }
    }

    /**
     * Checks if schema reading from headers is enabled.
     *
     * @return true if schema should be read from headers
     */
    public boolean isUseSchemaFromHeaders() {
        return useSchemaFromHeaders;
    }

    /**
     * Reads the schema content from the headers.
     *
     * @param headers the Kafka message headers
     * @return the schema content as a string, or null if not present
     */
    public String readSchemaFromHeaders(Headers headers) {
        Header header = headers.lastHeader(schemaHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    /**
     * Reads the schema type from the headers.
     *
     * @param headers the Kafka message headers
     * @return the schema type as a string (e.g., "AVRO", "PROTOBUF", "JSON"), or null if not present
     */
    public String readSchemaTypeFromHeaders(Headers headers) {
        Header header = headers.lastHeader(schemaTypeHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    /**
     * Writes the schema content to the headers.
     *
     * @param headers the Kafka message headers
     * @param schemaContent the schema content to write
     */
    public void writeSchemaToHeaders(Headers headers, String schemaContent) {
        if (schemaContent != null) {
            headers.add(schemaHeaderName, IoUtil.toBytes(schemaContent));
        }
    }

    /**
     * Writes the schema type to the headers.
     *
     * @param headers the Kafka message headers
     * @param schemaType the schema type to write (e.g., "AVRO", "PROTOBUF", "JSON")
     */
    public void writeSchemaTypeToHeaders(Headers headers, String schemaType) {
        if (schemaType != null) {
            headers.add(schemaTypeHeaderName, IoUtil.toBytes(schemaType));
        }
    }

}
