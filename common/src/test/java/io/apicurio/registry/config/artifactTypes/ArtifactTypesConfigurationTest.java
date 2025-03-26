package io.apicurio.registry.config.artifactTypes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class ArtifactTypesConfigurationTest {

    private static final String ARTIFACT_TYPES_CONFIG_SIMPLE = """
            {
                "includeStandardArtifactTypes": true
            }
            """;
    private static final String ARTIFACT_TYPES_CONFIG_EMPTY_ARTIFACT_TYPE = """
            {
                "includeStandardArtifactTypes": true,
                "artifactTypes": [
                    {
                        "artifactType": "RAML",
                        "name": "RAML",
                        "description": "The simplest way to model APIs.  Write once, use many. Creative laziness encouraged.",
                        "contentTypes": [
                            "application/json",
                            "application/x-yaml"
                        ]
                    }
                ]
            }
            """;
    private static final String ARTIFACT_TYPES_CONFIG_JAVA = """
            {
                "includeStandardArtifactTypes": true,
                "artifactTypes": [
                    {
                        "artifactType": "RAML",
                        "name": "RAML",
                        "description": "The simplest way to model APIs.  Write once, use many. Creative laziness encouraged.",
                        "contentTypes": [
                            "application/json",
                            "application/x-yaml"
                        ],
                        "contentAccepter": {
                            "type": "java",
                            "classname": "org.example.RAMLContentAccepter"
                        }
                    }
                ]
            }
            """;
    private static final String ARTIFACT_TYPES_CONFIG_SCRIPT = """
            {
                "includeStandardArtifactTypes": true,
                "artifactTypes": [
                    {
                        "artifactType": "RAML",
                        "name": "RAML",
                        "description": "The simplest way to model APIs.  Write once, use many. Creative laziness encouraged.",
                        "contentTypes": [
                            "application/json",
                            "application/x-yaml"
                        ],
                        "contentAccepter": {
                            "type": "script",
                            "scriptType": "lua",
                            "script": "LUA_SCRIPT_CONTENT"
                        }
                    }
                ]
            }
            """;
    private static final String ARTIFACT_TYPES_CONFIG_WEBHOOK = """
            {
                "includeStandardArtifactTypes": true,
                "artifactTypes": [
                    {
                        "artifactType": "RAML",
                        "name": "RAML",
                        "description": "The simplest way to model APIs.  Write once, use many. Creative laziness encouraged.",
                        "contentTypes": [
                            "application/json",
                            "application/x-yaml"
                        ],
                        "contentAccepter": {
                            "type": "webhook",
                            "url": "https://example.com/webhook-endpoint",
                            "method": "POST",
                            "headers": {
                                "Authorization": "Bearer YOUR_SECRET_TOKEN",
                                "Content-Type": "application/json"
                            },
                            "payload": {
                                "rpc": "validate",
                                "data": {
                                    "content": "${content}",
                                    "contentType": "${contentType}"
                                }
                            }
                        }
                    }
                ]
            }
            """;

    @Test
    void testLoadArtifactTypesConfiguration_Simple() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArtifactTypesConfiguration config = mapper.readValue(ARTIFACT_TYPES_CONFIG_SIMPLE, ArtifactTypesConfiguration.class);

        Assertions.assertNotNull(config);
        Assertions.assertTrue(config.getIncludeStandardArtifactTypes());
        Assertions.assertNotNull(config.getArtifactTypes());
        Assertions.assertEquals(0, config.getArtifactTypes().size());
    }

    @Test
    void testLoadArtifactTypesConfiguration_EmptyArtifactType() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArtifactTypesConfiguration config = mapper.readValue(ARTIFACT_TYPES_CONFIG_EMPTY_ARTIFACT_TYPE, ArtifactTypesConfiguration.class);

        Assertions.assertNotNull(config);
        Assertions.assertTrue(config.getIncludeStandardArtifactTypes());
        Assertions.assertNotNull(config.getArtifactTypes());
        Assertions.assertEquals(1, config.getArtifactTypes().size());
        Assertions.assertEquals("RAML", config.getArtifactTypes().get(0).getArtifactType());
        Assertions.assertEquals("RAML", config.getArtifactTypes().get(0).getName());
        Assertions.assertEquals("The simplest way to model APIs.  Write once, use many. Creative laziness encouraged.", config.getArtifactTypes().get(0).getDescription());
        Assertions.assertNotNull(config.getArtifactTypes().get(0).getContentTypes());
        Assertions.assertIterableEquals(List.of("application/json", "application/x-yaml"), config.getArtifactTypes().get(0).getContentTypes());
    }

    @Test
    void testLoadArtifactTypesConfiguration_Java() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArtifactTypesConfiguration config = mapper.readValue(ARTIFACT_TYPES_CONFIG_JAVA, ArtifactTypesConfiguration.class);

        Assertions.assertNotNull(config);
        Assertions.assertTrue(config.getIncludeStandardArtifactTypes());
        Assertions.assertNotNull(config.getArtifactTypes());
        Assertions.assertEquals(1, config.getArtifactTypes().size());
        Assertions.assertEquals("RAML", config.getArtifactTypes().get(0).getArtifactType());
        Assertions.assertEquals("RAML", config.getArtifactTypes().get(0).getName());
        Assertions.assertEquals("The simplest way to model APIs.  Write once, use many. Creative laziness encouraged.", config.getArtifactTypes().get(0).getDescription());
        Assertions.assertNotNull(config.getArtifactTypes().get(0).getContentTypes());
        Assertions.assertIterableEquals(List.of("application/json", "application/x-yaml"), config.getArtifactTypes().get(0).getContentTypes());

        Assertions.assertNotNull(config.getArtifactTypes().get(0).getContentAccepter());
        Assertions.assertEquals(JavaClassProvider.class, config.getArtifactTypes().get(0).getContentAccepter().getClass());
        Assertions.assertEquals("java", config.getArtifactTypes().get(0).getContentAccepter().getType());
        Assertions.assertEquals("org.example.RAMLContentAccepter", ((JavaClassProvider) config.getArtifactTypes().get(0).getContentAccepter()).getClassname());
    }

    @Test
    void testLoadArtifactTypesConfiguration_Script() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArtifactTypesConfiguration config = mapper.readValue(ARTIFACT_TYPES_CONFIG_SCRIPT, ArtifactTypesConfiguration.class);

        Assertions.assertNotNull(config.getArtifactTypes().get(0).getContentAccepter());
        Assertions.assertEquals(ScriptProvider.class, config.getArtifactTypes().get(0).getContentAccepter().getClass());
        Assertions.assertEquals("script", config.getArtifactTypes().get(0).getContentAccepter().getType());
        Assertions.assertEquals("lua", ((ScriptProvider) config.getArtifactTypes().get(0).getContentAccepter()).getScriptType());
        Assertions.assertEquals("LUA_SCRIPT_CONTENT", ((ScriptProvider) config.getArtifactTypes().get(0).getContentAccepter()).getScript());
    }

    @Test
    void testLoadArtifactTypesConfiguration_Webhook() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArtifactTypesConfiguration config = mapper.readValue(ARTIFACT_TYPES_CONFIG_WEBHOOK, ArtifactTypesConfiguration.class);

        Assertions.assertNotNull(config.getArtifactTypes().get(0).getContentAccepter());
        Assertions.assertEquals(WebhookProvider.class, config.getArtifactTypes().get(0).getContentAccepter().getClass());
        Assertions.assertEquals("webhook", config.getArtifactTypes().get(0).getContentAccepter().getType());
        Assertions.assertEquals("https://example.com/webhook-endpoint", ((WebhookProvider) config.getArtifactTypes().get(0).getContentAccepter()).getUrl());
        Assertions.assertEquals("POST", ((WebhookProvider) config.getArtifactTypes().get(0).getContentAccepter()).getMethod());
        Assertions.assertEquals(Map.of(
                "Authorization", "Bearer YOUR_SECRET_TOKEN",
                "Content-Type", "application/json"
        ), ((WebhookProvider) config.getArtifactTypes().get(0).getContentAccepter()).getHeaders());
        Assertions.assertEquals("validate", ((WebhookProvider) config.getArtifactTypes().get(0).getContentAccepter()).getPayload().get("rpc"));
        Assertions.assertEquals(Map.of(
                "content", "${content}",
                "contentType", "${contentType}"
        ), ((WebhookProvider) config.getArtifactTypes().get(0).getContentAccepter()).getPayload().get("data"));
    }

}
