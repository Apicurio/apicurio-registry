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

package io.apicurio.registry.serde;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.serde.config.DefaultSchemaResolverConfig;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.utils.IoUtil;

/**
 * Default implemntation of {@link SchemaResolver}
 *
 * @author Fabian Martinez
 */
public class DefaultSchemaResolver<S, T> extends AbstractSchemaResolver<S, T>{

    protected final Map<Long, SchemaLookupResult<S>> schemaCacheByContentId = new ConcurrentHashMap<>();

    private boolean autoCreateArtifact;
    private IfExists autoCreateBehavior;
    private boolean findLatest;

    /**
     * @see io.apicurio.registry.serde.AbstractSchemaResolver#reset()
     */
    @Override
    public void reset() {
        super.reset();
        schemaCacheByContentId.clear();
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#configure(java.util.Map, boolean, io.apicurio.registry.serde.SchemaParser)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey, SchemaParser<S> schemaParser) {
        super.configure(configs, isKey, schemaParser);

        DefaultSchemaResolverConfig config = new DefaultSchemaResolverConfig(configs);

        this.autoCreateArtifact = config.autoRegisterArtifact();
        this.autoCreateBehavior = IfExists.fromValue(config.autoRegisterArtifactIfExists());
        this.findLatest = config.findLatest();
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchema(java.lang.String, org.apache.kafka.common.header.Headers, java.lang.Object, io.apicurio.registry.serde.ParsedSchema)
     */
    @Override
    public SchemaLookupResult<S> resolveSchema(String topic, Headers headers, T data, ParsedSchema<S> parsedSchema) {
        Objects.requireNonNull(topic);
        Objects.requireNonNull(data);

        final ArtifactReference artifactReference = resolveArtifactReference(topic, headers, data, parsedSchema);

        Long globalId = globalIdCacheByArtifactReference.get(artifactReference);
        if (globalId != null) {
            SchemaLookupResult<S> schema = schemaCacheByGlobalId.get(globalId);
            if (schema != null) {
                return schema;
            }
        }

        if (autoCreateArtifact) {
            //keep operations with parsedSchema in it's own if sentences, because parsedSchema methods may have side effects
            if (parsedSchema != null && parsedSchema.getRawSchema() != null) {
                return handleAutoCreateArtifact(parsedSchema, artifactReference);
            }
        }

        if (findLatest || artifactReference.getVersion() != null) {
            return resolveSchemaByCoordinates(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion());
        }

        if (parsedSchema != null && parsedSchema.getRawSchema() != null) {
            return handleResolveSchemaByContent(parsedSchema, artifactReference);
        }

        return resolveSchemaByCoordinates(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion());
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchemaByArtifactReference(io.apicurio.registry.serde.strategy.ArtifactReference)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByArtifactReference(ArtifactReference reference) {
        //TODO add here more conditions whenever we support referencing by contentHash or some other thing
        if (reference.getContentId() != null) {
            return resolveSchemaByContentId(reference.getContentId());
        }
        if (reference.getGlobalId() == null) {
            return resolveSchemaByCoordinates(reference.getGroupId(), reference.getArtifactId(), reference.getVersion());
        } else {
            return resolveSchemaByGlobalId(reference.getGlobalId());
        }
    }

    private SchemaLookupResult<S> resolveSchemaByCoordinates(String groupId, String artifactId, String version) {
        if (artifactId == null) {
            throw new IllegalStateException("artifactId cannot be null");
        }

        ArtifactReference reference = ArtifactReference.builder().groupId(groupId).artifactId(artifactId).version(version).build();

        Long globalId = globalIdCacheByArtifactReference.compute(reference,
                artifactReference -> {
                    SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();
                    //TODO if getArtifactVersion returns the artifact version and globalid in the headers we can reduce this to only one http call
                    Long gid;
                    if (version == null) {
                        ArtifactMetaData metadata = client.getArtifactMetaData(groupId, artifactId);
                        loadFromArtifactMetaData(metadata, result);
                        gid = metadata.getGlobalId();
                    } else {
                        VersionMetaData metadata = client.getArtifactVersionMetaData(groupId, artifactId, version);
                        loadFromArtifactMetaData(metadata, result);
                        gid = metadata.getGlobalId();
                    }

                    InputStream rawSchema = client.getContentByGlobalId(gid);

                    byte[] schema = IoUtil.toBytes(rawSchema);
                    S parsed = schemaParser.parseSchema(schema);

                    result
                        .rawSchema(schema)
                        .schema(parsed);

                    schemaCacheByGlobalId.put(gid, result.build());
                    globalIdCacheByContent.put(IoUtil.toString(schema), gid);
                    return gid;
                });

        return schemaCacheByGlobalId.get(globalId);
    }

    protected SchemaLookupResult<S> resolveSchemaByContentId(long contentId) {
        return schemaCacheByContentId.computeIfAbsent(contentId, k -> {

            // it's impossible to retrieve more info about the artifact with only the contentId, and that's ok for this case
            InputStream rawSchema = client.getContentById(contentId);

            byte[] schema = IoUtil.toBytes(rawSchema);
            S parsed = schemaParser.parseSchema(schema);

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            return result
                    .contentId(contentId)
                    .rawSchema(schema)
                    .schema(parsed)
                    .build();
        });
    }

    /**
     * Search by content may not work for some usecases of our Serdes implementations.
     * For example when serializing protobuf messages, the schema inferred from the data
     * may not be equal to the .proto file schema uploaded in the registry.
     */
    private SchemaLookupResult<S> handleResolveSchemaByContent(ParsedSchema<S> parsedSchema,
            final ArtifactReference artifactReference) {
        byte[] rawSchema = parsedSchema.getRawSchema();
        String rawSchemaString = IoUtil.toString(rawSchema);

        Long globalId = globalIdCacheByContent.computeIfAbsent(rawSchemaString, key -> {
            VersionMetaData artifactMetadata = client.getArtifactVersionMetaDataByContent(artifactReference.getGroupId(), artifactReference.getArtifactId(), true, IoUtil.toStream(rawSchema));

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            loadFromArtifactMetaData(artifactMetadata, result);

            S schema = parsedSchema.getParsedSchema();
            result.rawSchema(rawSchema);
            result.schema(schema);

            Long artifactGlobalId = artifactMetadata.getGlobalId();
            schemaCacheByGlobalId.put(artifactGlobalId, result.build());
            globalIdCacheByArtifactReference.put(artifactReference, artifactGlobalId);
            return artifactGlobalId;
        });

        return schemaCacheByGlobalId.get(globalId);
    }

    private SchemaLookupResult<S> handleAutoCreateArtifact(ParsedSchema<S> parsedSchema,
            final ArtifactReference artifactReference) {
        byte[] rawSchema = parsedSchema.getRawSchema();
        String rawSchemaString = IoUtil.toString(rawSchema);

        Long globalId = globalIdCacheByContent.computeIfAbsent(rawSchemaString, key -> {
            ArtifactMetaData artifactMetadata = client.createArtifact(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion(), schemaParser.artifactType(), this.autoCreateBehavior, false, IoUtil.toStream(rawSchema));

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            loadFromArtifactMetaData(artifactMetadata, result);

            S schema = parsedSchema.getParsedSchema();
            result.rawSchema(rawSchema);
            result.schema(schema);

            Long newGlobalId = artifactMetadata.getGlobalId();
            schemaCacheByGlobalId.put(newGlobalId, result.build());
            globalIdCacheByArtifactReference.put(artifactReference, newGlobalId);
            return newGlobalId;
        });

        return schemaCacheByGlobalId.get(globalId);
    }

}
