/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.utils.converter.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import io.apicurio.registry.utils.IoUtil;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * @author Ales Justin
 */
public class PrettyFormatStrategy implements FormatStrategy {
    private final ObjectMapper mapper = new ObjectMapper();

    private String idName = "schemaId";
    private String payloadName = "payload";

    public PrettyFormatStrategy setIdName(String idName) {
        this.idName = idName;
        return this;
    }

    public PrettyFormatStrategy setPayloadName(String payloadName) {
        this.payloadName = payloadName;
        return this;
    }

    @Override
    public byte[] fromConnectData(long globalId, byte[] bytes) {
        String payload = IoUtil.toString(bytes);
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put(idName, globalId);
        root.putRawValue(payloadName, new RawValue(payload));
        try {
            return mapper.writeValueAsBytes(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public IdPayload toConnectData(byte[] bytes) {
        try {
            JsonNode root = mapper.readTree(bytes);
            long globalId = root.get(idName).asLong();
            String payload = root.get(payloadName).toString();
            byte[] payloadBytes = IoUtil.toBytes(payload);
            return new IdPayload(globalId, payloadBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
