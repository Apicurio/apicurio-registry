package io.apicurio.registry.serde.protobuf;

import java.util.Map;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import io.apicurio.registry.utils.IoUtil;


public class ProtobufSerdeHeaders extends MessageTypeSerdeHeaders {

    private String messageTypeNameHeaderName;

    /**
     * Constructor.
     * @param configs
     * @param isKey
     */
    public ProtobufSerdeHeaders(Map<String, Object> configs, boolean isKey) {
        super(configs, isKey);

        messageTypeNameHeaderName = "apicurio.protobuf.type";
        if (isKey) {
            messageTypeNameHeaderName += ".key";
        }

    }

    public String getProtobufTypeName(Headers headers) {
        Header header = headers.lastHeader(messageTypeNameHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    public void addProtobufTypeNameHeader(Headers headers, String protobufTypeName) {
        headers.add(messageTypeNameHeaderName, IoUtil.toBytes(protobufTypeName));
    }

}
