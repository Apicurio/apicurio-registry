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

package io.apicurio.registry.serde.protobuf;

import java.util.Map;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.serde.headers.MessageTypeSerdeHeaders;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Fabian Martinez
 */
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
