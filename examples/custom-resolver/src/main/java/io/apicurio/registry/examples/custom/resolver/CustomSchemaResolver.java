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

import io.apicurio.registry.resolver.AbstractSchemaResolver;
import io.apicurio.registry.resolver.ParsedSchema;
import io.apicurio.registry.resolver.SchemaLookupResult;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import org.apache.avro.Schema;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A custom schema resolver that simply uses the Avro schema found in the {@link Config} class - and ensures
 * that the schema exists in the registry (so that the deserializer is guaranteed to be able to retrieve the
 * exact schema used).
 *
 * @author eric.wittmann@gmail.com
 */
public class CustomSchemaResolver<D> extends AbstractSchemaResolver<Schema, D> {

    protected final Map<String, SchemaLookupResult<Schema>> schemaLookupCacheByContent = new ConcurrentHashMap<>();

    @Override
    public SchemaLookupResult<Schema> resolveSchemaByGlobalId(long globalId) {
        return super.resolveSchemaByGlobalId(globalId);
    }

    @Override
    public SchemaLookupResult<Schema> resolveSchema(Record<D> data) {
        System.out.println("[CustomSchemaResolver] Resolving a schema");
        String schema = Config.SCHEMA;

        return schemaLookupCacheByContent.computeIfAbsent(schema, (schemaData) -> {
            String groupId = "default";
            String artifactId = UUID.randomUUID() + "-value";

            ByteArrayInputStream schemaContent = new ByteArrayInputStream(
                    schema.getBytes(StandardCharsets.UTF_8));
            // Ensure the schema exists in the schema registry.

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactId(artifactId);
            createArtifact.setArtifactType(ArtifactType.AVRO);
            createArtifact.setFirstVersion(new CreateVersion());
            createArtifact.getFirstVersion().setContent(new VersionContent());
            createArtifact.getFirstVersion().getContent().setContent(IoUtil.toString(schemaContent));
            createArtifact.getFirstVersion().getContent().setContentType("application/json");

            final VersionMetaData metaData = client.groups()
                    .byGroupId("default").artifacts().post(createArtifact, config -> {
                        config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    }).getVersion();

            ParsedSchema<Schema> parsedSchema = this.schemaParser.getSchemaFromData(data);

            SchemaLookupResult.SchemaLookupResultBuilder<Schema> lookupResultBuilder = SchemaLookupResult.builder();

            SchemaLookupResult<Schema> result = lookupResultBuilder
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(String.valueOf(metaData.getVersion()))
                    .globalId(metaData.getGlobalId())
                    .parsedSchema(parsedSchema)
                    .build();

            // Also update the schemaCacheByGlobalId - useful if this resolver is used by both
            // the serializer and deserializer in the same Java application.
            return schemaCache.getByGlobalId(metaData.getGlobalId(), (id) -> result);
        });
    }

    @Override
    public SchemaLookupResult<Schema> resolveSchemaByArtifactReference(io.apicurio.registry.resolver.strategy.ArtifactReference reference) {
        throw new UnsupportedOperationException(
                "resolveSchemaByArtifactReference() is not supported by this implementation.");
    }
}
