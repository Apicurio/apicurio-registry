package io.apicurio.registry.utils.serde.util;

import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.AvroEncoding;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HeaderUtils {


    public static final String DEFAULT_HEADER_KEY_ARTIFACT_ID = "apicurio.key.artifactId";
    public static final String DEFAULT_HEADER_VALUE_ARTIFACT_ID = "apicurio.value.artifactId";
    public static final String DEFAULT_HEADER_KEY_VERSION = "apicurio.key.version";
    public static final String DEFAULT_HEADER_VALUE_VERSION = "apicurio.value.version";
    public static final String DEFAULT_HEADER_KEY_GLOBAL_ID = "apicurio.key.globalId";
    public static final String DEFAULT_HEADER_VALUE_GLOBAL_ID = "apicurio.value.globalId";
    public static final String HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME = "apicurio.key.artifactId.name";
    public static final String HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME = "apicurio.value.artifactId.name";
    public static final String HEADER_KEY_VERSION_OVERRIDE_NAME = "apicurio.key.version.name";
    public static final String HEADER_VALUE_VERSION_OVERRIDE_NAME = "apicurio.value.version.name";
    public static final String HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME = "apicurio.key.globalId.name";
    public static final String HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME = "apicurio.value.globalId.name";
    public static final String HEADER_KEY_ENCODING = "apicurio.key.encoding";
    public static final String HEADER_VALUE_ENCODING = "apicurio.value.encoding";


    protected String globalIdHeaderName;
    protected String artifactIdHeaderName;
    protected String versionHeaderName;
    protected String encodingName;

    public HeaderUtils(Map<String,Object> configs, boolean isKey) {
        if (isKey) {
            artifactIdHeaderName = (String) configs.getOrDefault(HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME, DEFAULT_HEADER_KEY_ARTIFACT_ID);
            globalIdHeaderName = (String) configs.getOrDefault(HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME, DEFAULT_HEADER_KEY_GLOBAL_ID);
            versionHeaderName = (String) configs.getOrDefault(HEADER_KEY_VERSION_OVERRIDE_NAME, DEFAULT_HEADER_KEY_VERSION);
            encodingName = HEADER_KEY_ENCODING;
        } else {
            artifactIdHeaderName = (String) configs.getOrDefault(HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME, DEFAULT_HEADER_VALUE_ARTIFACT_ID);
            globalIdHeaderName = (String) configs.getOrDefault(HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME, DEFAULT_HEADER_VALUE_GLOBAL_ID);
            versionHeaderName = (String) configs.getOrDefault(HEADER_VALUE_VERSION_OVERRIDE_NAME, DEFAULT_HEADER_VALUE_VERSION);
            encodingName = HEADER_VALUE_ENCODING;
        }
    }

    /**
     * Adds appropriate information to the Headers so that the deserializer can function properly.
     *
     * @param headers    msg headers
     * @param artifactId artifact id
     * @param globalId   global id
     */
    public void addSchemaHeaders(Headers headers, String artifactId, long globalId) {
        // we never actually set this requirement for the globalId to be non-negative ... but it mostly is ...
        if (headers == null) {
            headers = createHeaders();
        }
        if (globalId >= 0) {
            ByteBuffer buff = ByteBuffer.allocate(8);
            buff.putLong(globalId);
            headers.add(globalIdHeaderName, buff.array());
        } else {
            headers.add(artifactIdHeaderName, IoUtil.toBytes(artifactId));
        }
    }

    public void addEncodingHeader(Headers headers, AvroEncoding encoding) {
        headers.add(new RecordHeader(encodingName,encoding.name().getBytes()));
    }

    public AvroEncoding getEncoding(Headers headers) {
        Header encodingHeader = headers.lastHeader(encodingName);
        AvroEncoding encoding = null;
        if (encodingHeader != null) {
            encoding = AvroEncoding.valueOf(IoUtil.toString(encodingHeader.value()));
        }
        return encoding;
    }

    public String getArtifactId(Headers headers) {
        Header header = headers.lastHeader(artifactIdHeaderName);
        if (header == null) {
            throw new RuntimeException("ArtifactId not found in headers.");
        }
        return IoUtil.toString(header.value());
    }

    public Integer getVersion(Headers headers) {
        Header header = headers.lastHeader(versionHeaderName);
        if (header == null) {
            return null;
        }
        return ByteBuffer.wrap(header.value()).getInt();
    }

    public Long getGlobalId(Headers headers) {
        Header header = headers.lastHeader(globalIdHeaderName);
        if (header == null) {
            return null;
        }
        else {
            return ByteBuffer.wrap(header.value()).getLong();
        }
    }

    /**
     * Create an empty set of Kafka headers.
     */
    public static Headers createHeaders() {
        return createHeaders(Collections.emptyMap());
    }

    /**
     * Create Kafka headers from a map of String keys and String values.
     */
    public static Headers createHeaders(Map<String, String> mapObj) {
        Map<String, Header> headersMap = new HashMap<String, Header>();
        for (String key : mapObj.keySet()) {
            headersMap.put(key, new Header() {
                @Override
                public String key() {
                    return key;
                }
                @Override
                public byte[] value() {
                    return mapObj.get(key).getBytes();
                }
            });
        }
        return fromMap(headersMap);
    }


    private static Headers fromMap(Map<String, Header> mapObj) {
        return new Headers() {
            @Override
            public Iterator<Header> iterator() {
                return mapObj.values().iterator();
            }
            @Override
            public Header[] toArray() {
                return mapObj.values().toArray(new Header[0]);
            }

            @Override
            public Headers remove(String arg0) throws IllegalStateException {
                mapObj.remove(arg0);
                return fromMap(mapObj);
            }

            @Override
            public Header lastHeader(String arg0) {
                return mapObj.get(arg0);
            }

            @Override
            public Iterable<Header> headers(String arg0) {
                return mapObj.values();
            }

            @Override
            public Headers add(String arg0, byte[] arg1) throws IllegalStateException {
                Header newHeader = new Header() {
                    @Override
                    public String key() {
                        return arg0;
                    }

                    @Override
                    public byte[] value() {
                        return arg1;
                    }
                };
                mapObj.put(arg0, newHeader);
                return fromMap(mapObj);
            }

            @Override
            public Headers add(Header arg0) throws IllegalStateException {
                mapObj.put(arg0.key(), arg0);
                return fromMap(mapObj);
            }
        };
    }
}
