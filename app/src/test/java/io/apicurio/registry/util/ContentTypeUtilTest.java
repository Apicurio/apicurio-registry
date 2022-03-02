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

package io.apicurio.registry.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.tests.TestUtils;

/**
 * @author eric.wittmann@gmail.com
 */
class ContentTypeUtilTest {

    private static final String YAML_CONTENT = "openapi: 3.0.2\r\n" +
            "info:\r\n" +
            "    title: Empty API\r\n" +
            "    version: 1.0.0\r\n" +
            "paths:\r\n" +
            "    /mice:\r\n" +
            "        get:\r\n" +
            "            responses:\r\n" +
            "                '200':\r\n" +
            "                    description: ...\r\n" +
            "components:\r\n" +
            "    schemas:\r\n" +
            "        Mouse:\r\n" +
            "            description: ''\r\n" +
            "            type: object\r\n" +
            "";
    private static final String JSON_CONTENT = "{\r\n" +
            "  \"openapi\" : \"3.0.2\",\r\n" +
            "  \"info\" : {\r\n" +
            "    \"title\" : \"Empty API\",\r\n" +
            "    \"version\" : \"1.0.0\"\r\n" +
            "  },\r\n" +
            "  \"paths\" : {\r\n" +
            "    \"/mice\" : {\r\n" +
            "      \"get\" : {\r\n" +
            "        \"responses\" : {\r\n" +
            "          \"200\" : {\r\n" +
            "            \"description\" : \"...\"\r\n" +
            "          }\r\n" +
            "        }\r\n" +
            "      }\r\n" +
            "    }\r\n" +
            "  },\r\n" +
            "  \"components\" : {\r\n" +
            "    \"schemas\" : {\r\n" +
            "      \"Mouse\" : {\r\n" +
            "        \"description\" : \"\",\r\n" +
            "        \"type\" : \"object\"\r\n" +
            "      }\r\n" +
            "    }\r\n" +
            "  }\r\n" +
            "}";

    /**
     * Test method for {@link io.apicurio.registry.util.ContentTypeUtil#yamlToJson(io.apicurio.registry.content.ContentHandle)}.
     */
    @Test
    void testYamlToJson() throws Exception {
        ContentHandle yaml = ContentHandle.create(YAML_CONTENT);
        ContentHandle json = ContentTypeUtil.yamlToJson(yaml);
        Assertions.assertEquals(TestUtils.normalizeMultiLineString(JSON_CONTENT), TestUtils.normalizeMultiLineString(json.content()));
    }

}
