/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry;

import java.util.Collections;
import java.util.Map;

import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.json.JsonConverter;
import org.apache.kafka.connect.json.JsonConverterConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Ales Justin
 */
public class JsonConverterTest {
    @Test
    public void testOriginalJson() {
        testJson(true);
        testJson(false);
    }

    private void testJson(boolean enableSchemas) {
        JsonConverter converter = new JsonConverter();
        converter.configure(Collections.singletonMap(JsonConverterConfig.SCHEMAS_ENABLE_CONFIG, enableSchemas), false);

        org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
                                                               .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
                                                               .build();
        Struct struct = new Struct(sc);
        struct.put("bar", "somebar");

        byte[] bytes = converter.fromConnectData("qwerty123", sc, struct);

        Object result = converter.toConnectData("qwerty123", bytes).value();
        Object value = (result instanceof Struct) ? Struct.class.cast(result).get("bar") : Map.class.cast(result).get("bar");
        Assertions.assertEquals("somebar", value.toString());
        
        converter.close();
    }
}