package io.apicurio.tests.serdes.apicurio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.tests.common.serdes.json.Msg;
import io.apicurio.tests.common.serdes.json.ValidMessage;
import io.apicurio.registry.utils.IoUtil;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;

public class JsonSchemaMsgFactory {

    private String jsonSchema = "{" +
            "    \"$id\": \"https://example.com/message.schema.json\"," +
            "    \"$schema\": \"http://json-schema.org/draft-07/schema#\"," +
            "    \"required\": [" +
            "        \"message\"," +
            "        \"time\"" +
            "    ]," +
            "    \"type\": \"object\"," +
            "    \"properties\": {" +
            "        \"message\": {" +
            "            \"description\": \"\"," +
            "            \"type\": \"string\"" +
            "        }," +
            "        \"time\": {" +
            "            \"description\": \"\"," +
            "            \"type\": \"number\"" +
            "        }" +
            "    }" +
            "}";

    public ValidMessage generateMessage(int count) {
        ValidMessage msg = new ValidMessage();
        msg.setMessage("Hello " + count);
        msg.setTime(new Date().getTime());
        return msg;
    }

    public JsonNode generateMessageJsonNode(int count) {
        ValidMessage msg = new ValidMessage();
        msg.setMessage("Hello " + count);
        msg.setTime(new Date().getTime());
        return new ObjectMapper().valueToTree(msg);
    }

    public boolean validateAsMap(Map<String, Object> map) {
        String msg = (String) map.get("message");
        Long time = (Long) map.get("time");
        return msg != null && time != null;
    }

    public boolean validateMessage(Msg message) {
        return message instanceof ValidMessage;
    }

    public InputStream getSchemaStream() {
        return IoUtil.toStream(jsonSchema);
    }

    public byte[] getSchemaBytes() {
        return IoUtil.toBytes(jsonSchema);
    }

}
