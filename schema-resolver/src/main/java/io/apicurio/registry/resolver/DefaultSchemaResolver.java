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

package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of {@link SchemaResolver}
 *
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
public class DefaultSchemaResolver<S, T> extends AbstractSchemaResolver<S, T> {

    private boolean autoCreateArtifact;
    private IfExists autoCreateBehavior;
    private boolean findLatest;

    /**
     * @see io.apicurio.registry.resolver.AbstractSchemaResolver#reset()
     */
    @Override
    public void reset() {
        super.reset();
    }

    /**
     * @see io.apicurio.registry.resolver.AbstractSchemaResolver#configure(java.util.Map, io.apicurio.registry.resolver.SchemaParser)
     */
    @Override
    public void configure(Map<String, ?> configs, SchemaParser<S, T> schemaParser) {
        super.configure(configs, schemaParser);

        if (artifactResolverStrategy.loadSchema() && !schemaParser.supportsExtractSchemaFromData()) {
            throw new IllegalStateException("Wrong configuration");
        }

        this.autoCreateArtifact = config.autoRegisterArtifact();
        this.autoCreateBehavior = IfExists.fromValue(config.autoRegisterArtifactIfExists());
        this.findLatest = config.findLatest();
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#resolveSchema(io.apicurio.registry.resolver.data.Record)
     */
    @Override
    public SchemaLookupResult<S> resolveSchema(Record<T> data) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(data.payload());


        ParsedSchema<S> parsedSchema = null;
        if (artifactResolverStrategy.loadSchema() && schemaParser.supportsExtractSchemaFromData()) {
            parsedSchema = schemaParser.getSchemaFromData(data);
        }

        final ArtifactReference artifactReference = resolveArtifactReference(data, parsedSchema, false, null);

        return getSchemaFromCache(artifactReference)
                .orElse(getSchemaFromRegistry(parsedSchema, data, artifactReference));
    }

    private Optional<SchemaLookupResult<S>> getSchemaFromCache(ArtifactReference artifactReference) {
        if (artifactReference.getGlobalId() != null && schemaCache.containsByGlobalId(artifactReference.getGlobalId())) {
            return Optional.of(resolveSchemaByGlobalId(artifactReference.getGlobalId()));
        } else if (artifactReference.getContentId() != null && schemaCache.containsByContentId(artifactReference.getContentId())) {
            return Optional.of(resolveSchemaByContentId(artifactReference.getContentId()));
        } else if (schemaCache.containsByArtifactCoordinates(ArtifactCoordinates.fromArtifactReference(artifactReference))) {
            return Optional.of(resolveSchemaByArtifactCoordinatesCached(ArtifactCoordinates.fromArtifactReference(artifactReference)));
        }
        return Optional.empty();
    }


    private SchemaLookupResult<S> getSchemaFromRegistry(ParsedSchema<S> parsedSchema, Record<T> data, ArtifactReference artifactReference) {

        if (autoCreateArtifact && schemaParser.supportsExtractSchemaFromData()) {
            if (parsedSchema == null) {
                parsedSchema = schemaParser.getSchemaFromData(data);
            }

            if (parsedSchema.hasReferences()) {
                //List of references lookup, to be used to create the references for the artifact
                final List<SchemaLookupResult<S>> schemaLookupResults = handleArtifactReferences(data, parsedSchema);
                return handleAutoCreateArtifact(parsedSchema, artifactReference, schemaLookupResults);
            } else {
                return handleAutoCreateArtifact(parsedSchema, artifactReference);
            }
        }

        if (findLatest || artifactReference.getVersion() != null) {
            return resolveSchemaByCoordinates(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion());
        }

        if (schemaParser.supportsExtractSchemaFromData()) {
            if (parsedSchema == null) {
                parsedSchema = schemaParser.getSchemaFromData(data);
            }
            return handleResolveSchemaByContent(parsedSchema, artifactReference);
        }

        return resolveSchemaByCoordinates(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion());
    }

    private List<SchemaLookupResult<S>> handleArtifactReferences(Record<T> data, ParsedSchema<S> parsedSchema) {
        final List<SchemaLookupResult<S>> referencesLookup = new ArrayList<>();

        for (ParsedSchema<S> referencedSchema : parsedSchema.getSchemaReferences()) {

            List<SchemaLookupResult<S>> nestedReferences = handleArtifactReferences(data, referencedSchema);

            if (nestedReferences.isEmpty()) {
                referencesLookup.add(handleAutoCreateArtifact(referencedSchema, resolveArtifactReference(data, referencedSchema, true, referencedSchema.referenceName())));
            } else {
                referencesLookup.add(handleAutoCreateArtifact(referencedSchema, resolveArtifactReference(data, referencedSchema, true, referencedSchema.referenceName()), nestedReferences));
            }
        }
        return referencesLookup;
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#resolveSchemaByArtifactReference (io.apicurio.registry.resolver.strategy.ArtifactReferenceImpl)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByArtifactReference(ArtifactReference reference) {
        if (reference == null) {
            throw new IllegalStateException("artifact reference cannot be null");
        }
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

        return resolveSchemaByArtifactReferenceCached(reference);
    }

    protected SchemaLookupResult<S> resolveSchemaByContentId(long contentId) {
        return schemaCache.getByContentId(contentId, contentIdKey -> {

            // it's impossible to retrieve more info about the artifact with only the contentId, and that's ok for this case
            InputStream rawSchema = client.getContentById(contentIdKey);

            //Get the artifact references
            final List<io.apicurio.registry.rest.v2.beans.ArtifactReference> artifactReferences = client.getArtifactReferencesByContentId(contentId);
            //If there are any references for the schema being parsed, resolve them before parsing the schema
            final Map<String, ParsedSchema<S>> resolvedReferences = resolveReferences(artifactReferences);

            byte[] schema = IoUtil.toBytes(rawSchema);
            S parsed = schemaParser.parseSchema(schema, resolvedReferences);

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            ParsedSchemaImpl<S> ps = new ParsedSchemaImpl<S>()
                    .setParsedSchema(parsed)
                    .setRawSchema(schema);

            return result
                    .contentId(contentIdKey)
                    .parsedSchema(ps)
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

        String rawSchemaString = IoUtil.toString(parsedSchema.getRawSchema());

        return schemaCache.getByContent(rawSchemaString, contentKey -> {

            VersionMetaData artifactMetadata = client.getArtifactVersionMetaDataByContent(
                    artifactReference.getGroupId(), artifactReference.getArtifactId(), true, IoUtil.toStream(contentKey));

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            loadFromArtifactMetaData(artifactMetadata, result);

            result.parsedSchema(parsedSchema);

            return result.build();
        });
    }

    private SchemaLookupResult<S> handleAutoCreateArtifact(ParsedSchema<S> parsedSchema,
                                                           final ArtifactReference artifactReference) {
        String rawSchemaString = IoUtil.toString(parsedSchema.getRawSchema());

        return schemaCache.getByContent(rawSchemaString, contentKey -> {

            ArtifactMetaData artifactMetadata = client.createArtifact(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion(),
                    schemaParser.artifactType(), this.autoCreateBehavior, false, IoUtil.toStream(parsedSchema.getRawSchema()));

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            loadFromArtifactMetaData(artifactMetadata, result);

            result.parsedSchema(parsedSchema);

            return result.build();
        });
    }

    private SchemaLookupResult<S> handleAutoCreateArtifact(ParsedSchema<S> parsedSchema,
                                                           final ArtifactReference artifactReference, List<SchemaLookupResult<S>> referenceLookups) {

        String rawSchemaString = IoUtil.toString(parsedSchema.getRawSchema());

        final List<io.apicurio.registry.rest.v2.beans.ArtifactReference> artifactReferences = parseReferences(referenceLookups);

        return schemaCache.getByContent(rawSchemaString, contentKey -> {

            ArtifactMetaData artifactMetadata = client.createArtifact(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion(),
                    schemaParser.artifactType(), this.autoCreateBehavior, false, null, null, ContentTypes.APPLICATION_CREATE_EXTENDED, null, null, IoUtil.toStream(parsedSchema.getRawSchema()), artifactReferences);

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            loadFromArtifactMetaData(artifactMetadata, result);

            result.parsedSchema(parsedSchema);

            return result.build();
        });
    }

    private List<io.apicurio.registry.rest.v2.beans.ArtifactReference> parseReferences(List<SchemaLookupResult<S>> referenceLookups) {
        final List<io.apicurio.registry.rest.v2.beans.ArtifactReference> artifactReferences = new ArrayList<>();

        referenceLookups.forEach(referenceLookup -> {
            io.apicurio.registry.rest.v2.beans.ArtifactReference artifactReferenceLookup = new io.apicurio.registry.rest.v2.beans.ArtifactReference();
            artifactReferenceLookup.setArtifactId(referenceLookup.getArtifactId());
            artifactReferenceLookup.setGroupId(referenceLookup.getGroupId());
            artifactReferenceLookup.setName(referenceLookup.getParsedSchema().referenceName());
            artifactReferenceLookup.setVersion(referenceLookup.getVersion());
            artifactReferences.add(artifactReferenceLookup);
        });

        return artifactReferences;
    }

    private SchemaLookupResult<S> resolveSchemaByArtifactCoordinatesCached(ArtifactCoordinates artifactCoordinates) {
        return schemaCache.getByArtifactCoordinates(artifactCoordinates, artifactCoordinatesKey -> resolveByCoordinates(artifactCoordinatesKey.getGroupId(), artifactCoordinatesKey.getArtifactId(), artifactCoordinatesKey.getVersion()));
    }


    private SchemaLookupResult<S> resolveSchemaByArtifactReferenceCached(ArtifactReference artifactReference) {
        if (artifactReference.getGlobalId() != null) {
            return schemaCache.getByGlobalId(artifactReference.getGlobalId(), this::resolveSchemaByGlobalId);
        } else if (artifactReference.getContentId() != null) {
            return schemaCache.getByContentId(artifactReference.getContentId(), this::resolveSchemaByContentId);
        } else {
            return schemaCache.getByArtifactCoordinates(ArtifactCoordinates.fromArtifactReference(artifactReference), artifactReferenceKey -> resolveByCoordinates(artifactReferenceKey.getGroupId(), artifactReferenceKey.getArtifactId(), artifactReferenceKey.getVersion()));
        }
    }

    private SchemaLookupResult<S> resolveByCoordinates(String groupId, String artifactId, String version) {
        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();
        //TODO if getArtifactVersion returns the artifact version and globalid in the headers we can reduce this to only one http call
        Long gid;
        if (version == null) {
            ArtifactMetaData metadata = client.getArtifactMetaData(groupId, artifactId);
            loadFromArtifactMetaData(metadata, result);
            gid = metadata.getGlobalId();
        } else {
            VersionMetaData metadata = client.getArtifactVersionMetaData(
                    groupId, artifactId, version);
            loadFromArtifactMetaData(metadata, result);
            gid = metadata.getGlobalId();
        }

        InputStream rawSchema = client.getContentByGlobalId(gid);

        //Get the artifact references
        final List<io.apicurio.registry.rest.v2.beans.ArtifactReference> artifactReferences = client.getArtifactReferencesByGlobalId(gid);
        //If there are any references for the schema being parsed, resolve them before parsing the schema
        final Map<String, ParsedSchema<S>> resolvedReferences = resolveReferences(artifactReferences);

        byte[] schema = IoUtil.toBytes(rawSchema);
        S parsed = schemaParser.parseSchema(schema, resolvedReferences);

        result.parsedSchema(new ParsedSchemaImpl<S>()
                .setParsedSchema(parsed)
                .setRawSchema(schema));

        return result.build();
    }
}
