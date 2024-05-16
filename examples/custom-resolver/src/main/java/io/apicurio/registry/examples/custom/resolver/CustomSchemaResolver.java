/*
 * Copyright 2020 JBoss Inc
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

package io.apicurio.registry.examples.custom.resolver;

import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.serde.AbstractSchemaResolver;
import io.apicurio.registry.serde.ParsedSchema;
import io.apicurio.registry.serde.SchemaLookupResult;
import io.apicurio.registry.serde.SchemaParser;
import io.apicurio.registry.serde.avro.AvroSchemaUtils;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import org.apache.avro.Schema;
import org.apache.kafka.common.header.Headers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A custom schema resolver that simply uses the Avro schema found in the {@link Config}
 * class - and ensures that the schema exists in the registry (so that the deserializer
 * is guaranteed to be able to retrieve the exact schema used).
 *
 * @author eric.wittmann@gmail.com
 */
public class CustomSchemaResolver<D> extends AbstractSchemaResolver<Schema, D> {

    protected final Map<String, SchemaLookupResult<Schema>> schemaLookupCacheByContent = new ConcurrentHashMap<>();

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#configure(java.util.Map, boolean, io.apicurio.registry.serde.SchemaParser)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey, SchemaParser<Schema> schemaMapper) {
        super.configure(configs, isKey, schemaMapper);
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchema(java.lang.String, org.apache.kafka.common.header.Headers, java.lang.Object, io.apicurio.registry.serde.ParsedSchema)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public SchemaLookupResult<Schema> resolveSchema(String topic, Headers headers, D data, ParsedSchema<Schema> parsedSchema) {
        System.out.println("[CustomSchemaResolver] Resolving a schema for topic: " + topic);
        String schema = Config.SCHEMA;

        return schemaLookupCacheByContent.computeIfAbsent(schema, (schemaData) -> {
            String groupId = "default";
            String artifactId = topic + "-value";
            Schema schemaObj = AvroSchemaUtils.parse(schema);

            ByteArrayInputStream schemaContent = new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8));
            // Ensure the schema exists in the schema registry.

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactId(artifactId);
            createArtifact.setType(ArtifactType.AVRO);
            createArtifact.setFirstVersion(new CreateVersion());
            createArtifact.getFirstVersion().setContent(new VersionContent());
            createArtifact.getFirstVersion().getContent().setContent(IoUtil.toString(schemaContent));
            createArtifact.getFirstVersion().getContent().setContentType("application/json");

            final io.apicurio.registry.rest.client.models.VersionMetaData metaData = client.groups().byGroupId("default").artifacts().post(createArtifact, config -> {
                config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
            }).getVersion();

            SchemaLookupResult result = SchemaLookupResult.builder()
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(String.valueOf(metaData.getVersion()))
                    .globalId(metaData.getGlobalId())
                    .schema(schemaObj)
                    .rawSchema(schema.getBytes(StandardCharsets.UTF_8))
                    .build();

            // Also update the schemaCacheByGlobalId - useful if this resolver is used by both
            // the serializer and deserializer in the same Java application.
            return schemaCache.getByGlobalId(metaData.getGlobalId(), (id) -> result);
        });
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchemaByArtifactReference(io.apicurio.registry.serde.strategy.ArtifactReference)
     */
    @Override
    public SchemaLookupResult<Schema> resolveSchemaByArtifactReference(ArtifactReference reference) {
        throw new UnsupportedOperationException("resolveSchemaByArtifactReference() is not supported by this implementation.");
    }

    @Override
    public SchemaLookupResult<Schema> resolveSchemaByGlobalId(long globalId) {
        return super.resolveSchemaByGlobalId(globalId);
    }

}
