package io.apicurio.registry.customTypes;

import io.quarkus.test.junit.QuarkusTestProfile;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ScriptArtifactTypesTestProfile implements QuarkusTestProfile {

    private static final String CONFIG_FILE = """
            {
                "includeStandardArtifactTypes": false,
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
                            "scriptType": "contentAccepter",
                            "script": "src/test/resources/script/js/dist/out.js"
                        },
                        "contentCanonicalizer": {
                            "type": "script",
                            "scriptType": "contentCanonicalizer",
                            "script": "src/test/resources/script/js/dist/out.js"
                        },
                        "contentValidator": {
                            "type": "script",
                            "scriptType": "contentValidator",
                            "script": "src/test/resources/script/js/dist/out.js"
                        },
                        "compatibilityChecker": {
                            "type": "script",
                            "scriptType": "compatibilityChecker",
                            "script": "src/test/resources/script/js/dist/out.js"
                        },
                        "contentDereferencer": {
                            "type": "script",
                            "scriptType": "contentDereferencer",
                            "script": "src/test/resources/script/js/dist/out.js"
                        },
                        "referenceFinder": {
                            "type": "script",
                            "scriptType": "referenceFinder",
                            "script": "src/test/resources/script/js/dist/out.js"
                        }
                    }
                ]
            }
            """;

    @Override
    public Map<String, String> getConfigOverrides() {
        File configFile = createTestConfig();

        Map<String, String> props = new HashMap<>();
        props.put("apicurio.artifact-types.config-file", configFile.getAbsolutePath());

        return props;
    }

    private static @NotNull File createTestConfig() {
        FileOutputStream fos = null;
        try {
            File tempFile = File.createTempFile("_CustomArtifactTypesTestProfile_apicurio-registry-artifact-types.", ".json");
            fos = new FileOutputStream(tempFile);
            IOUtils.write(CONFIG_FILE, fos, StandardCharsets.UTF_8);
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(fos);
        }
    }

}
