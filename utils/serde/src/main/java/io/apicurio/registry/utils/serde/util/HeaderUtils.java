/*
 * Copyright 2020 IBM
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
package io.apicurio.registry.utils.serde.util;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.AvroEncoding;
import io.apicurio.registry.utils.serde.SerdeConfig;
import io.apicurio.registry.utils.serde.SerdeHeaders;

/**
 * Class used to add header information to Kafka messages when passing artifactId/globalId information
 * from the serializer to the deserializer via message headers instead of via the message payload.
 */
public class HeaderUtils {

    private final String globalIdHeaderName;
    private final String artifactIdHeaderName;
    private final String versionHeaderName;
    private final String encodingHeaderName;
    private final String messageTypeHeaderName;

    public HeaderUtils(Map<String,Object> configs, boolean isKey) {
        if (isKey) {
            artifactIdHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_KEY_ARTIFACT_ID_OVERRIDE_NAME, SerdeHeaders.HEADER_KEY_ARTIFACT_ID);
            globalIdHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_KEY_GLOBAL_ID_OVERRIDE_NAME, SerdeHeaders.HEADER_KEY_GLOBAL_ID);
            versionHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_KEY_VERSION_OVERRIDE_NAME, SerdeHeaders.HEADER_KEY_VERSION);
            encodingHeaderName = SerdeHeaders.HEADER_KEY_ENCODING;
            messageTypeHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_KEY_MESSAGE_TYPE_OVERRIDE_NAME, SerdeHeaders.HEADER_KEY_MESSAGE_TYPE);
            
        } else {
            artifactIdHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_VALUE_ARTIFACT_ID_OVERRIDE_NAME, SerdeHeaders.HEADER_VALUE_ARTIFACT_ID);
            globalIdHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_VALUE_GLOBAL_ID_OVERRIDE_NAME, SerdeHeaders.HEADER_VALUE_GLOBAL_ID);
            versionHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_VALUE_VERSION_OVERRIDE_NAME, SerdeHeaders.HEADER_VALUE_VERSION);
            encodingHeaderName = SerdeHeaders.HEADER_VALUE_ENCODING;
            messageTypeHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_VALUE_MESSAGE_TYPE_OVERRIDE_NAME, SerdeHeaders.HEADER_VALUE_MESSAGE_TYPE);
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
        headers.add(new RecordHeader(encodingHeaderName, encoding.name().getBytes()));
    }

    public void addMessageTypeHeader(Headers headers, String messageType) {
        if (headers == null) {
            headers = createHeaders();
        }
        headers.add(messageTypeHeaderName, IoUtil.toBytes(messageType));
    }

    public AvroEncoding getEncoding(Headers headers) {
        Header encodingHeader = headers.lastHeader(encodingHeaderName);
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

    public String getMessageType(Headers headers) {
        Header header = headers.lastHeader(messageTypeHeaderName);
        if (header == null) {
            throw new RuntimeException("Message Type not found in headers.");
        }
        return IoUtil.toString(header.value());
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
