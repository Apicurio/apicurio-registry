package io.apicurio.registry.noprofile.validity;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class ValidityRuleApplicationTest extends AbstractResourceTestBase {

    private static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";
    private static final String INVALID_SCHEMA = "{\"type\": \"string";

    private static final String SCHEMA_WITH_MAP = """
            {
                "type": "record",
                "name": "userInfo",
                "namespace": "my.example",
                "fields": [
                    {
                        "name": "name",
                        "type": "string",
                        "default": "NONE"
                    },
                    {
                        "name": "props",
                        "type": {
                            "type": "map",
                            "values": "string"
                        }
                    }
                ]
            }""";
    private static final String INVALID_SCHEMA_WITH_MAP = """
            {
                "type": "record",
                "name": "userInfo",
                "namespace": "my.example",
                "fields": [
                    {
                        "name": "name",
                        "type": "string",
                        "default": "NONE"
                    },
                    {
                        "name": "props",
                        "type": {
                            "type": "map",
                            "values": "string"
                        },
                        "default": "{}"
                    }
                ]
            }""";

    @Test
    public void testValidityRuleApplication() throws Exception {
        String artifactId = "ValidityRuleApplicationTest";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_SIMPLE, ContentTypes.APPLICATION_JSON);
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId(artifactId).rules().post(createRule);

        var exception = Assertions.assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            createArtifactVersion(artifactId, INVALID_SCHEMA, ContentTypes.APPLICATION_JSON);
        });
        assertEquals("RuleViolationException", exception.getName());
        assertEquals(409, exception.getErrorCode());
    }

    @Test
    public void testValidityRuleApplication_Map() throws Exception {
        String artifactId = "testValidityRuleApplication_Map";
        createArtifact(artifactId, ArtifactType.AVRO, SCHEMA_WITH_MAP, ContentTypes.APPLICATION_JSON);
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                .byArtifactId(artifactId).rules().post(createRule);

        var exception = Assertions.assertThrows(io.apicurio.registry.rest.client.models.Error.class, () -> {
            createArtifactVersion(artifactId, INVALID_SCHEMA_WITH_MAP, ContentTypes.APPLICATION_JSON);
        });
        assertEquals("RuleViolationException", exception.getName());
        assertEquals(409, exception.getErrorCode());
    }

}
