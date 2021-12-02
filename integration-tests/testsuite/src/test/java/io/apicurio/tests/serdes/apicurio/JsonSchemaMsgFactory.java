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

package io.apicurio.tests.serdes.apicurio;

import java.io.InputStream;
import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.utils.IoUtil;
import io.apicurio.tests.common.serdes.json.Msg;
import io.apicurio.tests.common.serdes.json.ValidMessage;

/**
 * @author Fabian Martinez
 */
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
