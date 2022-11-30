/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.noprofile.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.resolver.DefaultSchemaResolver;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaParser;
import io.apicurio.registry.resolver.SchemaResolver;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class SchemaResolverTest extends AbstractResourceTestBase {

    private RegistryClient restClient;

    @BeforeEach
    public void createIsolatedClient() {
        restClient = RegistryClientFactory.create(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    @Test
    public void testDynamicStrategy() throws Exception {

        SchemaResolver<Schema, GenericRecord> resolver = new DefaultSchemaResolver<>();
        resolver.setClient(restClient);
        Map<String, Object> config = new HashMap<>();
        config.put(SchemaResolverConfig.FIND_LATEST_ARTIFACT, true);
        resolver.configure(config, new SchemaParser<Schema, GenericRecord>() {

            @Override
            public Schema parseSchema(byte[] rawSchema, Map<String, ParsedSchema<Schema>> resolvedReferences) {
                return null;
            }

            @Override
            public ParsedSchema<Schema> getSchemaFromData(Record<GenericRecord> data) {
                return null;
            }

            /**
             * @see io.apicurio.registry.resolver.SchemaParser#supportsExtractSchemaFromData()
             */
            @Override
            public boolean supportsExtractSchemaFromData() {
                return false;
            }

            @Override
            public String artifactType() {
                return ArtifactType.AVRO;
            }
        });

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        String artifactId = TestUtils.generateArtifactId();
        createArtifact(artifactId, ArtifactType.AVRO, schema.toString());

        GenericRecord avroRecord = new GenericData.Record(schema);
        avroRecord.put("bar", "somebar");
        Record<GenericRecord> record = new CustomResolverRecord(avroRecord, ArtifactReference.builder().artifactId(artifactId).build());
        var lookup = resolver.resolveSchema(record);

        assertNull(lookup.getGroupId());
        assertEquals(artifactId, lookup.getArtifactId());
        assertEquals(schema.toString(), new String(lookup.getParsedSchema().getRawSchema()));
        assertNull(lookup.getParsedSchema().getParsedSchema());

        Assertions.assertThrows(ArtifactNotFoundException.class, () -> resolver.resolveSchema(new CustomResolverRecord(avroRecord, ArtifactReference.builder().artifactId("foo").build())));
        resolver.close();
    }

    private static class CustomResolverRecord implements Record<GenericRecord> {

        private GenericRecord payload;
        private ArtifactReference reference;

        public CustomResolverRecord(GenericRecord payload, ArtifactReference reference) {
            this.payload = payload;
            this.reference = reference;
        }

        /**
         * @see io.apicurio.registry.resolver.data.Record#metadata()
         */
        @Override
        public Metadata metadata() {
            return new Metadata() {

                @Override
                public ArtifactReference artifactReference() {
                    return reference;
                }
            };
        }

        /**
         * @see io.apicurio.registry.resolver.data.Record#payload()
         */
        @Override
        public GenericRecord payload() {
            return payload;
        }

    }

}
