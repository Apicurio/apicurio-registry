package io.apicurio.registry.serde.headers;

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

    /**
     * @see io.apicurio.registry.serde.headers.HeadersHandler#configure(java.util.Map, boolean)
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
        } else {
            globalIdHeaderName = config.getValueGlobalIdHeader();
            contentIdHeaderName = config.getValueContentIdHeader();
            contentHashHeaderName = config.getValueContentHashHeader();
            groupIdHeaderName = config.getValueGroupIdHeader();
            artifactIdHeaderName = config.getValueArtifactIdHeader();
            versionHeaderName = config.getValueVersionHeader();
        }
        idOption = config.useIdOption();
    }

    /**
     * @see io.apicurio.registry.serde.headers.HeadersHandler#writeHeaders(org.apache.kafka.common.header.Headers,
     *      io.apicurio.registry.resolver.SchemaLookupResult)
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
     * @see io.apicurio.registry.serde.headers.HeadersHandler#readHeaders(org.apache.kafka.common.header.Headers)
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

}
