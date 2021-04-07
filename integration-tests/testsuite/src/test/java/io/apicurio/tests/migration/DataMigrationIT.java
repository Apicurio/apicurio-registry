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

package io.apicurio.tests.migration;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.types.RuleType;
import io.apicurio.tests.common.ApicurioRegistryBaseIT;
import io.apicurio.tests.common.Constants;
import io.apicurio.tests.common.RegistryFacade;
import io.apicurio.tests.serdes.apicurio.AvroGenericRecordSchemaFactory;
import io.apicurio.tests.serdes.apicurio.JsonSchemaMsgFactory;

/**
 * @author Fabian Martinez
 */
@Tag(Constants.MIGRATION)
public class DataMigrationIT extends ApicurioRegistryBaseIT {

    private RegistryFacade registryFacade = RegistryFacade.getInstance();

    @Test
    public void migrate() throws Exception {

        RegistryClient source = RegistryClientFactory.create(registryFacade.getSourceRegistryUrl());

        List<Long> globalIds = new ArrayList<>();

        JsonSchemaMsgFactory jsonSchema = new JsonSchemaMsgFactory();
        for (int idx = 0; idx < 50; idx++) {
            String artifactId = idx + "-" + UUID.randomUUID().toString();
            var amd = source.createArtifact("default", artifactId, jsonSchema.getSchemaStream());
            retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            globalIds.add(amd.getGlobalId());
        }

        for (int idx = 0; idx < 15; idx++) {
            AvroGenericRecordSchemaFactory avroSchema = new AvroGenericRecordSchemaFactory(List.of("a" + idx));
            String artifactId = "avro-" + idx;

            var amd = source.createArtifact("avro-schemas", artifactId, avroSchema.generateSchemaStream());
            retry(() -> source.getContentByGlobalId(amd.getGlobalId()));
            globalIds.add(amd.getGlobalId());

            var vmd = source.updateArtifact("avro-schemas", artifactId, avroSchema.generateSchemaStream());
            retry(() -> source.getContentByGlobalId(vmd.getGlobalId()));
            globalIds.add(vmd.getGlobalId());
        }

        Rule rule = new Rule();
        rule.setType(RuleType.VALIDITY);
        rule.setConfig("SYNTAX_ONLY");
        source.createArtifactRule("avro-schemas", "avro-0", rule);

        rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        source.createGlobalRule(rule);

        RegistryClient dest = RegistryClientFactory.create(registryFacade.getDestRegistryUrl());

        dest.importData(source.exportData());

        retry(() -> {
            for (long gid : globalIds) {
                dest.getContentByGlobalId(gid);
            }
            assertTrue(dest.getArtifactRuleConfig("avro-schemas", "avro-0", RuleType.VALIDITY).getConfig().equals("SYNTAX_ONLY"));
            assertTrue(dest.getGlobalRuleConfig(RuleType.COMPATIBILITY).getConfig().equals("BACKWARD"));
        });
    }

}
