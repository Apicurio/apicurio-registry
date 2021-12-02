/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.serde.headers;

import java.util.Map;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.SerdeHeaders;
import io.apicurio.registry.utils.IoUtil;

/**
 * Common utility class for serializers and deserializers that use config properties such as {@link SerdeConfig#HEADER_VALUE_MESSAGE_TYPE_OVERRIDE_NAME}
 *
 * @author Fabian Martinez
 */
public class MessageTypeSerdeHeaders {

    private final String messageTypeHeaderName;

    public MessageTypeSerdeHeaders(Map<String, Object> configs, boolean isKey) {
        if (isKey) {
            messageTypeHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_KEY_MESSAGE_TYPE_OVERRIDE_NAME, SerdeHeaders.HEADER_KEY_MESSAGE_TYPE);
        } else {
            messageTypeHeaderName = (String) configs.getOrDefault(SerdeConfig.HEADER_VALUE_MESSAGE_TYPE_OVERRIDE_NAME, SerdeHeaders.HEADER_VALUE_MESSAGE_TYPE);
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