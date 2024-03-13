package io.apicurio.registry.noprofile.validity;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.ArtifactContent;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class ValidityRuleApplicationTest extends AbstractResourceTestBase {

    private static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";
    private static final String INVALID_SCHEMA = "{\"type\": \"string";

    private static final String SCHEMA_WITH_MAP = "{\r\n" +
            "    \"type\": \"record\",\r\n" +
            "    \"name\": \"userInfo\",\r\n" +
            "    \"namespace\": \"my.example\",\r\n" +
            "    \"fields\": [\r\n" +
            "        {\r\n" +
            "            \"name\": \"name\",\r\n" +
            "            \"type\": \"string\",\r\n" +
            "            \"default\": \"NONE\"\r\n" +
            "        },\r\n" +
            "        {\r\n" +
            "            \"name\": \"props\",\r\n" +
            "            \"type\": {\r\n" +
            "                \"type\": \"map\",\r\n" +
            "                \"values\": \"string\"\r\n" +
            "            }\r\n" +
            "        }\r\n" +
            "    ]\r\n" +
            "}";
    private static final String INVALID_SCHEMA_WITH_MAP = "{\r\n" +
            "    \"type\": \"record\",\r\n" +
            "    \"name\": \"userInfo\",\r\n" +
            "    \"namespace\": \"my.example\",\r\n" +
            "    \"fields\": [\r\n" +
            "        {\r\n" +
            "            \"name\": \"name\",\r\n" +
            "            \"type\": \"string\",\r\n" +
            "            \"default\": \"NONE\"\r\n" +
            "        },\r\n" +
            "        {\r\n" +
            "            \"name\": \"props\",\r\n" +
            "            \"type\": {\r\n" +
            "                \"type\": \"map\",\r\n" +
            "                \"values\": \"string\"\r\n" +
            "            },\r\n" +
            "            \"default\": \"{}\"\r\n" +
            "        }\r\n" +
            "    ]\r\n" +
            "}";

    @Test
    public void testValidityRuleApplication() throws Exception {
        String artifactId = "ValidityRuleApplicationTest";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_SIMPLE);
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().byArtifactId(artifactId).rules().post(rule);

        var exception = Assertions.assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            ArtifactContent content = new ArtifactContent();
            content.setContent(INVALID_SCHEMA);
            clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().byArtifactId(artifactId).versions().post(content);
        });
        assertEquals("RuleViolationException", exception.getName());
        assertEquals(409, exception.getErrorCode());
    }

    @Test
    public void testValidityRuleApplication_Map() throws Exception {
        String artifactId = "testValidityRuleApplication_Map";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP);
        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().byArtifactId(artifactId).rules().post(rule);

        var exception = Assertions.assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            ArtifactContent content = new ArtifactContent();
            content.setContent(INVALID_SCHEMA_WITH_MAP);
            clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts().byArtifactId(artifactId).versions().post(content);
        });
        assertEquals("RuleViolationException", exception.getName());
        assertEquals(409, exception.getErrorCode());
    }

}
