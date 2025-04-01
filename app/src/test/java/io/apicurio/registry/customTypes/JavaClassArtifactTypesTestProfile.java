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

public class JavaClassArtifactTypesTestProfile implements QuarkusTestProfile {

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
                            "type": "java",
                            "classname": "io.apicurio.utils.test.raml.microsvc.RamlContentAccepter"
                        },
                        "contentCanonicalizer": {
                            "type": "java",
                            "classname": "io.apicurio.utils.test.raml.microsvc.RamlContentCanonicalizer"
                        },
                        "contentValidator": {
                            "type": "java",
                            "classname": "io.apicurio.utils.test.raml.microsvc.RamlContentValidator"
                        },
                        "compatibilityChecker": {
                            "type": "java",
                            "classname": "io.apicurio.utils.test.raml.microsvc.RamlCompatibilityChecker"
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