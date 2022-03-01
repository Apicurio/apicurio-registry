/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.utils.impexp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author eric.wittmann@gmail.com
 */
class EntityReaderTest {

    /**
     * Test method for {@link io.apicurio.registry.noprofile.rest.v2.impexp.EntityReader#readEntity()}.
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

            while ( (entity = reader.readEntity()) != null ) {
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
     * @param resourceName the resource name
     */
    protected final InputStream resourceToInputStream(String resourceName) {
        InputStream stream = getClass().getResourceAsStream(resourceName);
        Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
        return stream;
    }
}
