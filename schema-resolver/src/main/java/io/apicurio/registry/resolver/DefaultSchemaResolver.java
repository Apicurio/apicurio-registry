package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.client.RegistryArtifactReference;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.client.RegistryVersionCoordinates;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactCoordinates;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.utils.IoUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

/**
 * Default implementation of {@link SchemaResolver}
 */
public class DefaultSchemaResolver<S, T> extends AbstractSchemaResolver<S, T> {

    private boolean autoCreateArtifact;
    private String autoCreateBehavior;
    private boolean findLatest;

    private static final Logger logger = Logger.getLogger(DefaultSchemaResolver.class.getSimpleName());

    public DefaultSchemaResolver() {
        super();
    }

    public DefaultSchemaResolver(RegistryClientFacade clientFacade) {
        this.clientFacade = clientFacade;
    }

    /**
     * @see io.apicurio.registry.resolver.AbstractSchemaResolver#reset()
     */
    @Override
    public void reset() {
        super.reset();
    }

    /**
     * @see io.apicurio.registry.resolver.AbstractSchemaResolver#configure(java.util.Map,
     *      io.apicurio.registry.resolver.SchemaParser)
     */
    @Override
    public void configure(Map<String, ?> configs, SchemaParser<S, T> schemaParser) {
        super.configure(configs, schemaParser);

        if (artifactResolverStrategy.loadSchema() && !schemaParser.supportsExtractSchemaFromData()) {
            throw new IllegalStateException("Wrong configuration");
        }

        this.autoCreateArtifact = config.autoRegisterArtifact();
        this.autoCreateBehavior = config.autoRegisterArtifactIfExists();
        this.findLatest = config.findLatest();
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#resolveSchema(io.apicurio.registry.resolver.data.Record)
     */
    @Override
    public SchemaLookupResult<S> resolveSchema(Record<T> data) {
        Objects.requireNonNull(data);
        Objects.requireNonNull(data.payload());

        ParsedSchema<S> parsedSchema;
        if (artifactResolverStrategy.loadSchema() && schemaParser.supportsExtractSchemaFromData()) {
            parsedSchema = schemaParser.getSchemaFromData(data, resolveDereferenced);
        } else {
            parsedSchema = null;
        }

        final ArtifactReference artifactReference = resolveArtifactReference(data, parsedSchema, false, null);

        return getSchemaFromCache(artifactReference)
                .orElseGet(() -> getSchemaFromRegistry(parsedSchema, data, artifactReference));
    }

    private Optional<SchemaLookupResult<S>> getSchemaFromCache(ArtifactReference artifactReference) {
        if (artifactReference.getGlobalId() != null
                && schemaCache.containsByGlobalId(artifactReference.getGlobalId())) {
            return Optional.of(resolveSchemaByGlobalId(artifactReference.getGlobalId()));
        } else if (artifactReference.getContentId() != null
                && schemaCache.containsByContentId(artifactReference.getContentId())) {
            return Optional.of(resolveSchemaByContentId(artifactReference.getContentId()));
        } else if (artifactReference.getContentHash() != null
                && schemaCache.containsByContentHash(artifactReference.getContentHash())) {
            return Optional.of(resolveSchemaByContentHash(artifactReference.getContentHash()));
        } else if (schemaCache.containsByArtifactCoordinates(
                ArtifactCoordinates.fromArtifactReference(artifactReference))) {
            return Optional.of(resolveSchemaByArtifactCoordinatesCached(
                    ArtifactCoordinates.fromArtifactReference(artifactReference)));
        }
        return Optional.empty();
    }

    private SchemaLookupResult<S> getSchemaFromRegistry(ParsedSchema<S> parsedSchema, Record<T> data,
            ArtifactReference artifactReference) {

        if (autoCreateArtifact) {

            if (schemaParser.supportsExtractSchemaFromData()) {

                if (parsedSchema == null) {
                    parsedSchema = schemaParser.getSchemaFromData(data, resolveDereferenced);
                }

                if (parsedSchema.hasReferences()) {
                    // List of references lookup, to be used to create the references for the artifact
                    final List<SchemaLookupResult<S>> schemaLookupResults = handleArtifactReferences(data,
                            parsedSchema);
                    return handleAutoCreateArtifact(parsedSchema, artifactReference, schemaLookupResults);
                } else {
                    return handleAutoCreateArtifact(parsedSchema, artifactReference);
                }
            } else if (config.getExplicitSchemaLocation() != null
                    && schemaParser.supportsGetSchemaFromLocation()) {
                parsedSchema = schemaParser.getSchemaFromLocation(config.getExplicitSchemaLocation());
                return handleAutoCreateArtifact(parsedSchema, artifactReference);
            }
        }

        if (findLatest || artifactReference.getVersion() != null) {
            return resolveSchemaByCoordinates(artifactReference.getGroupId(),
                    artifactReference.getArtifactId(), artifactReference.getVersion());
        }

        if (schemaParser.supportsExtractSchemaFromData()) {
            if (parsedSchema == null) {
                parsedSchema = schemaParser.getSchemaFromData(data, resolveDereferenced);
            }
            return handleResolveSchemaByContent(parsedSchema, artifactReference);
        }

        return resolveSchemaByCoordinates(artifactReference.getGroupId(), artifactReference.getArtifactId(),
                artifactReference.getVersion());
    }

    private List<SchemaLookupResult<S>> handleArtifactReferences(Record<T> data,
            ParsedSchema<S> parsedSchema) {
        final List<SchemaLookupResult<S>> referencesLookup = new ArrayList<>();

        for (ParsedSchema<S> referencedSchema : parsedSchema.getSchemaReferences()) {

            List<SchemaLookupResult<S>> nestedReferences = handleArtifactReferences(data, referencedSchema);

            if (nestedReferences.isEmpty()) {
                referencesLookup.add(handleAutoCreateArtifact(referencedSchema, resolveArtifactReference(data,
                        referencedSchema, true, referencedSchema.referenceName())));
            } else {
                referencesLookup.add(handleAutoCreateArtifact(referencedSchema, resolveArtifactReference(data,
                        referencedSchema, true, referencedSchema.referenceName()), nestedReferences));
            }
        }
        return referencesLookup;
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#resolveSchemaByArtifactReference
     *      (io.apicurio.registry.resolver.strategy.ArtifactReferenceImpl)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByArtifactReference(ArtifactReference reference) {
        if (reference == null) {
            throw new IllegalStateException("artifact reference cannot be null");
        }

        if (reference.getContentId() != null) {
            return resolveSchemaByContentId(reference.getContentId());
        }
        if (reference.getContentHash() != null) {
            return resolveSchemaByContentHash(reference.getContentHash());
        }
        if (reference.getGlobalId() != null) {
            return resolveSchemaByGlobalId(reference.getGlobalId());
        }

        return resolveSchemaByCoordinates(reference.getGroupId(), reference.getArtifactId(),
                reference.getVersion());
    }

    private SchemaLookupResult<S> resolveSchemaByCoordinates(String groupId, String artifactId,
            String version) {
        if (artifactId == null) {
            throw new IllegalStateException("artifactId cannot be null");
        }

        ArtifactReference reference = ArtifactReference.builder().groupId(groupId).artifactId(artifactId)
                .version(version).build();

        return resolveSchemaByArtifactReferenceCached(reference);
    }

    protected SchemaLookupResult<S> resolveSchemaByContentId(long contentId) {
        return schemaCache.getByContentId(contentId, contentIdKey -> {

            // it's impossible to retrieve more info about the artifact with only the contentId, and that's ok
            // for this case
            ParsedSchemaImpl<S> ps = null;
            String rawSchema = this.clientFacade.getSchemaByContentId(contentIdKey);

            // Get the artifact references
            final List<RegistryArtifactReference> artifactReferences = this.clientFacade.getReferencesByContentId(contentId);
            // If there are any references for the schema being parsed, resolve them before parsing the
            // schema
            final Map<String, ParsedSchema<S>> resolvedReferences = resolveReferences(artifactReferences);

            byte[] schema = rawSchema.getBytes(StandardCharsets.UTF_8);
            S parsed = schemaParser.parseSchema(schema, resolvedReferences);

            ps = new ParsedSchemaImpl<S>().setParsedSchema(parsed).setRawSchema(schema);

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();
            return result.contentId(contentIdKey).parsedSchema(ps).build();
        });
    }

    protected SchemaLookupResult<S> resolveSchemaByContentHash(String contentHash) {
        return schemaCache.getByContentHash(contentHash, contentHashKey -> {
            // it's impossible to retrieve more info about the artifact with only the contentHash, and that's
            // ok for this case
            ParsedSchemaImpl<S> ps = null;

            String rawSchema = this.clientFacade.getSchemaByContentHash(contentHashKey);

            // Get the artifact references
            final List<RegistryArtifactReference> artifactReferences = this.clientFacade.getReferencesByContentHash(contentHashKey);
            // If there are any references for the schema being parsed, resolve them before parsing the schema
            final Map<String, ParsedSchema<S>> resolvedReferences = resolveReferences(artifactReferences);

            byte[] schema = rawSchema.getBytes(StandardCharsets.UTF_8);
            S parsed = schemaParser.parseSchema(schema, resolvedReferences);

            ps = new ParsedSchemaImpl<S>().setParsedSchema(parsed).setRawSchema(schema);
            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            return result.contentHash(contentHashKey).parsedSchema(ps).build();
        });
    }

    /**
     * Search by content may not work for some use cases of our Serdes implementations. For example when
     * serializing protobuf messages, the schema inferred from the data may not be equal to the .proto file
     * schema uploaded in the registry.
     */
    private SchemaLookupResult<S> handleResolveSchemaByContent(ParsedSchema<S> parsedSchema,
            final ArtifactReference artifactReference) {

        String rawSchemaString = IoUtil.toString(parsedSchema.getRawSchema());

        return schemaCache.getByContent(rawSchemaString, contentKey -> {
            logger.info(String.format("Retrieving schema content using string: %s", rawSchemaString));

            String artifactType = schemaParser.artifactType();

            List<RegistryVersionCoordinates> versions = this.clientFacade.searchVersionsByContent(rawSchemaString,
                    artifactType, artifactReference, true);
            if (versions.isEmpty()) {
                versions = this.clientFacade.searchVersionsByContent(rawSchemaString, artifactType, artifactReference, false);

                if (versions.isEmpty()) {
                    throw new RuntimeException(
                            String.format("Could not resolve artifact reference by content: %s",
                                    rawSchemaString) + "&" + artifactReference);
                }
            }

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            loadFromVersionCoordinates(versions.get(0), result);

            result.parsedSchema(parsedSchema);

            return result.build();
        });
    }

    private SchemaLookupResult<S> handleAutoCreateArtifact(ParsedSchema<S> parsedSchema,
            final ArtifactReference artifactReference) {
        String rawSchemaString = IoUtil.toString(parsedSchema.getRawSchema());

        return schemaCache.getByContent(rawSchemaString, contentKey -> {
            String artifactType = schemaParser.artifactType();
            String groupId = artifactReference.getGroupId() == null ? "default" : artifactReference.getGroupId();
            String artifactId = artifactReference.getArtifactId();
            String version = artifactReference.getVersion();
            String autoCreate = this.autoCreateBehavior;
            boolean canonical = false;
            RegistryVersionCoordinates versionCoordinates = this.clientFacade.createSchema(artifactType, groupId, artifactId,
                    version, autoCreate, canonical, rawSchemaString, List.of());

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            loadFromVersionCoordinates(versionCoordinates, result);

            result.parsedSchema(parsedSchema);

            return result.build();
        });
    }

    private SchemaLookupResult<S> handleAutoCreateArtifact(ParsedSchema<S> parsedSchema,
            final ArtifactReference artifactReference, List<SchemaLookupResult<S>> referenceLookups) {

        String rawSchemaString = IoUtil.toString(parsedSchema.getRawSchema());

        return schemaCache.getByContent(rawSchemaString, contentKey -> {
            String artifactType = schemaParser.artifactType();
            String groupId = artifactReference.getGroupId() == null ? "default" : artifactReference.getGroupId();
            String artifactId = artifactReference.getArtifactId();
            String version = artifactReference.getVersion();
            String autoCreate = this.autoCreateBehavior;
            boolean canonical = false;
            List<RegistryArtifactReference> artifactReferences = referenceLookups.stream().map(
                    RegistryArtifactReference::fromSchemaLookupResult).toList();
            RegistryVersionCoordinates versionCoordinates = this.clientFacade.createSchema(artifactType, groupId, artifactId,
                    version, autoCreate, canonical, rawSchemaString, artifactReferences);

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            loadFromVersionCoordinates(versionCoordinates, result);

            result.parsedSchema(parsedSchema);

            return result.build();
        });
    }

    private SchemaLookupResult<S> resolveSchemaByArtifactCoordinatesCached(
            ArtifactCoordinates artifactCoordinates) {
        return schemaCache.getByArtifactCoordinates(artifactCoordinates,
                artifactCoordinatesKey -> resolveByCoordinates(artifactCoordinatesKey.getGroupId(),
                        artifactCoordinatesKey.getArtifactId(), artifactCoordinatesKey.getVersion()));
    }

    private SchemaLookupResult<S> resolveSchemaByArtifactReferenceCached(
            ArtifactReference artifactReference) {
        if (artifactReference.getGlobalId() != null) {
            return schemaCache.getByGlobalId(artifactReference.getGlobalId(), this::resolveSchemaByGlobalId);
        } else if (artifactReference.getContentId() != null) {
            return schemaCache.getByContentId(artifactReference.getContentId(),
                    this::resolveSchemaByContentId);
        } else if (artifactReference.getContentHash() != null) {
            return schemaCache.getByContentHash(artifactReference.getContentHash(),
                    this::resolveSchemaByContentHash);
        } else {
            return schemaCache.getByArtifactCoordinates(
                    ArtifactCoordinates.fromArtifactReference(artifactReference),
                    artifactReferenceKey -> resolveByCoordinates(artifactReferenceKey.getGroupId(),
                            artifactReferenceKey.getArtifactId(), artifactReferenceKey.getVersion()));
        }
    }

    private SchemaLookupResult<S> resolveByCoordinates(String groupId, String artifactId, String version) {
        // TODO if getArtifactVersion returns the artifact version and globalid in the headers we can reduce
        // this to only one http call

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

        // Get the version metadata (globalId, contentId, etc)
        RegistryVersionCoordinates versionCoordinates = this.clientFacade.getVersionCoordinatesByGAV(groupId, artifactId, version);

        loadFromVersionCoordinates(versionCoordinates, result);

        // Get the schema string (either dereferenced or not based on config)
        String schemaString = this.clientFacade.getSchemaByGlobalId(versionCoordinates.getGlobalId(), resolveDereferenced);
        Map<String, ParsedSchema<S>> resolvedReferences = new HashMap<>();
        // If resolving dereference, we need to also fetch and resolve the references
        if (!resolveDereferenced) {
            List<RegistryArtifactReference> artifactReferences = this.clientFacade.getReferencesByGlobalId(versionCoordinates.getGlobalId());
            // If there are any references for the schema being parsed, resolve them before parsing the schema
            resolvedReferences = resolveReferences(artifactReferences);
        }

        byte[] schema = schemaString.getBytes(StandardCharsets.UTF_8);
        S parsed = schemaParser.parseSchema(schema, resolvedReferences);

        result.parsedSchema(new ParsedSchemaImpl<S>().setParsedSchema(parsed).setRawSchema(schema));

        return result.build();
    }
}
