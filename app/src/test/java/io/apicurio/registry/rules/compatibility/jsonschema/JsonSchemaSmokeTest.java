/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.rules.compatibility.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffContext;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary.findDifferences;

/**
 *
 */
public class JsonSchemaSmokeTest extends AbstractRegistryTestBase {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JsonOrgModule());
    }


    @Test
    public void testComparison() throws Exception {
        // TODO Do this automatically

        // Case 1
        DiffContext dctx = null;
        dctx = findDifferences(
            resourceToString("schema-4.1.json"),
            resourceToString("schema-4.2.json"));
        System.out.println(dctx);
    }
}
