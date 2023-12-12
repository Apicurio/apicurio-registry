package io.apicurio.registry.utils.impexp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

class EntityReaderTest {

    /**
     * Test method for {@link io.apicurio.registry.noprofile.rest.v3.impexp.EntityReader#readEntity()}.
     */
    @Test
    void testReadEntity() throws Exception {
        try (InputStream data = resourceToInputStream("export.zip")) {
            ZipInputStream zip = new ZipInputStream(data, StandardCharsets.UTF_8);
            EntityReader reader = new EntityReader(zip);
            Entity entity = null;

            int contentCounter = 0;
            int versionCounter = 0;
            int globalRuleCounter = 0;
            int artyRuleCounter = 0;

            while ((entity = reader.readEntity()) != null) {
                if (entity instanceof ContentEntity) {
                    contentCounter++;
                }
                if (entity instanceof ArtifactVersionEntity) {
                    versionCounter++;
                }
                if (entity instanceof ArtifactRuleEntity) {
                    artyRuleCounter++;
                }
                if (entity instanceof GlobalRuleEntity) {
                    globalRuleCounter++;
                }
            }

            Assertions.assertEquals(1003, contentCounter);
            Assertions.assertEquals(5, versionCounter);
            Assertions.assertEquals(1, artyRuleCounter);
            Assertions.assertEquals(1, globalRuleCounter);
        }
    }

    /**
     * Loads a resource as an input stream.
     * 
     * @param resourceName the resource name
     */
    protected final InputStream resourceToInputStream(String resourceName) {
        InputStream stream = getClass().getResourceAsStream(resourceName);
        Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
        return stream;
    }
}
