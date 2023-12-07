package io.apicurio.registry.utils.converter.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.RawValue;
import io.apicurio.registry.utils.IoUtil;

import java.io.IOException;
import java.io.UncheckedIOException;

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
