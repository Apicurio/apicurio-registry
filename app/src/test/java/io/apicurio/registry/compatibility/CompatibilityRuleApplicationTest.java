/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.compatibility;

import javax.inject.Inject;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleApplicationType;
import io.apicurio.registry.rules.RuleContext;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.RulesService;
import io.apicurio.registry.rules.compatibility.CompatibilityRuleExecutor;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RuleConfigurationDto;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.types.RuleType;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Jakub Senko <jsenko@redhat.com>
 */
@QuarkusTest
public class CompatibilityRuleApplicationTest extends AbstractResourceTestBase {

    private static final String SCHEMA_SIMPLE = "{\"type\": \"string\"}";

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    RulesService rules;

    @Inject
    CompatibilityRuleExecutor compatibility;

    @Test
    public void testGlobalCompatibilityRuleNoArtifact() {
        // this should NOT throw an exception
        storage.createGlobalRule(RuleType.COMPATIBILITY, RuleConfigurationDto.builder().configuration("FULL").build());
        rules.applyRules("not-existent", ArtifactType.AVRO, ContentHandle.create(SCHEMA_SIMPLE),
            RuleApplicationType.CREATE);
    }
    
    @Test
    public void testAvroCompatibility() {
        String v1Schema = "{\"type\":\"record\",\"namespace\":\"com.example\",\"name\":\"FullName\",\"fields\":[{\"name\":\"first\",\"type\":\"string\"},{\"name\":\"last\",\"type\":\"string\"}]}";
        String v2Schema = "{\"type\": \"string\"}";
        
        Assertions.assertThrows(RuleViolationException.class, () -> {
            RuleContext context = new RuleContext("Test", ArtifactType.AVRO, "BACKWARD", ContentHandle.create(v1Schema), ContentHandle.create(v2Schema));
            compatibility.execute(context);
        });
    }
}
