package io.apicurio.registry.serde.headers;

import io.apicurio.registry.utils.IoUtil;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import java.util.Map;

/**
 * Common utility class for serializers and deserializers that use config properties such as
 * {@link SerdeConfig#HEADER_VALUE_MESSAGE_TYPE_OVERRIDE_NAME}
 */
public class MessageTypeSerdeHeaders {

    private final String messageTypeHeaderName;

    public MessageTypeSerdeHeaders(Map<String, Object> configs, boolean isKey) {
        if (isKey) {
            messageTypeHeaderName = (String) configs.getOrDefault(
                    SerdeConfig.HEADER_KEY_MESSAGE_TYPE_OVERRIDE_NAME, SerdeHeaders.HEADER_KEY_MESSAGE_TYPE);
        } else {
            messageTypeHeaderName = (String) configs.getOrDefault(
                    SerdeConfig.HEADER_VALUE_MESSAGE_TYPE_OVERRIDE_NAME,
                    SerdeHeaders.HEADER_VALUE_MESSAGE_TYPE);
        }
    }

    public String getMessageType(Headers headers) {
        Header header = headers.lastHeader(messageTypeHeaderName);
        if (header == null) {
            return null;
        }
        return IoUtil.toString(header.value());
    }

    public void addMessageTypeHeader(Headers headers, String messageType) {
        headers.add(messageTypeHeaderName, IoUtil.toBytes(messageType));
    }

}