package io.apicurio.registry.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.utils.tests.TestUtils;

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
