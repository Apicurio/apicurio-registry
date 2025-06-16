package io.apicurio.registry.customTypes;

import io.quarkus.test.junit.QuarkusTestProfile;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class JavascriptArtifactTypesTestProfile implements QuarkusTestProfile {

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
                        "scriptLocation": "js-artifact-types-test/dist/js-artifact-types-test.js",
                        "contentAccepter": {
                            "type": "script"
                        },
                        "contentCanonicalizer": {
                            "type": "script"
                        },
                        "contentValidator": {
                            "type": "script"
                        },
                        "compatibilityChecker": {
                            "type": "script"
                        },
                        "contentDereferencer": {
                            "type": "script"
                        },
                        "referenceFinder": {
                            "type": "script"
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

    private static File createTestConfig() {
        FileOutputStream fos = null;
        try {
            File tempFile = File.createTempFile("_JavascriptArtifactTypesTestProfile_apicurio-registry-artifact-types.", ".json");
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