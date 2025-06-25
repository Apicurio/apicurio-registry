package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.client.RegistryArtifactReference;
import io.apicurio.registry.resolver.client.RegistrySDK;
import io.apicurio.registry.resolver.client.RegistrySDKFactory;
import io.apicurio.registry.resolver.client.RegistryVersionCoordinates;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.utils.IoUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Base implementation of {@link SchemaResolver}
 */
public abstract class AbstractSchemaResolver<S, T> implements SchemaResolver<S, T> {

    protected final ERCache<SchemaLookupResult<S>> schemaCache = new ERCache<>();

    protected SchemaResolverConfig config;
    protected SchemaParser<S, T> schemaParser;
    protected RegistrySDK sdk;
    protected ArtifactReferenceResolverStrategy<S, T> artifactResolverStrategy;

    protected String explicitArtifactGroupId;
    protected String explicitArtifactId;
    protected String explicitArtifactVersion;

    protected boolean resolveDereferenced;

    @Override
    public void configure(Map<String, ?> configs, SchemaParser<S, T> schemaParser) {
        this.schemaParser = schemaParser;

        this.config = new SchemaResolverConfig(configs);
        if (sdk == null) {
            sdk = RegistrySDKFactory.createSDK(config);
        }

        Object ais = config.getArtifactResolverStrategy();
        Utils.instantiate(ArtifactReferenceResolverStrategy.class, ais, this::setArtifactResolverStrategy);

        schemaCache.configureLifetime(config.getCheckPeriod());
        schemaCache.configureRetryBackoff(config.getRetryBackoff());
        schemaCache.configureRetryCount(config.getRetryCount());
        schemaCache.configureCacheLatest(config.getCacheLatest());
        schemaCache.configureFaultTolerantRefresh(config.getFaultTolerantRefresh());

        schemaCache.configureGlobalIdKeyExtractor(SchemaLookupResult::getGlobalId);
        schemaCache.configureContentKeyExtractor(schema -> Optional
                .ofNullable(schema.getParsedSchema().getRawSchema()).map(IoUtil::toString).orElse(null));
        schemaCache.configureContentIdKeyExtractor(SchemaLookupResult::getContentId);
        schemaCache.configureContentHashKeyExtractor(SchemaLookupResult::getContentHash);
        schemaCache.configureArtifactCoordinatesKeyExtractor(SchemaLookupResult::toArtifactCoordinates);
        schemaCache.checkInitialized();

        String groupIdOverride = config.getExplicitArtifactGroupId();
        if (groupIdOverride != null) {
            this.explicitArtifactGroupId = groupIdOverride;
        }
        String artifactIdOverride = config.getExplicitArtifactId();
        if (artifactIdOverride != null) {
            this.explicitArtifactId = artifactIdOverride;
        }
        String artifactVersionOverride = config.getExplicitArtifactVersion();
        if (artifactVersionOverride != null) {
            this.explicitArtifactVersion = artifactVersionOverride;
        }

        this.resolveDereferenced = config.resolveDereferenced();
    }

    @Override
    public void setSDK(RegistrySDK sdk) {
        this.sdk = sdk;
    }

    /**
     * @param artifactResolverStrategy the artifactResolverStrategy to set
     */
    @Override
    public void setArtifactResolverStrategy(
            ArtifactReferenceResolverStrategy<S, T> artifactResolverStrategy) {
        this.artifactResolverStrategy = artifactResolverStrategy;
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#getSchemaParser()
     */
    @Override
    public SchemaParser<S, T> getSchemaParser() {
        return this.schemaParser;
    }

    /**
     * Resolve an artifact reference for the given record, and optional parsed schema. This will use the
     * artifact resolver strategy and then override the values from that strategy with any explicitly
     * configured values (groupId, artifactId, version).
     *
     * @param data
     * @param parsedSchema
     * @param isReference
     * @return artifact reference
     */
    protected ArtifactReference resolveArtifactReference(Record<T> data, ParsedSchema<S> parsedSchema,
            boolean isReference, String referenceArtifactId) {
        ArtifactReference artifactReference = artifactResolverStrategy.artifactReference(data, parsedSchema);
        artifactReference = ArtifactReference.builder()
                .groupId(this.explicitArtifactGroupId == null ? artifactReference.getGroupId()
                    : this.explicitArtifactGroupId)
                .artifactId(resolveArtifactId(artifactReference.getArtifactId(), isReference,
                        referenceArtifactId))
                .version(this.explicitArtifactVersion == null ? artifactReference.getVersion()
                    : this.explicitArtifactVersion)
                .build();

        return artifactReference;
    }

    protected String resolveArtifactId(String artifactId, boolean isReference, String referenceArtifactId) {
        if (isReference) {
            return referenceArtifactId;
        } else {
            return this.explicitArtifactId == null ? artifactId : this.explicitArtifactId;
        }
    }

    protected SchemaLookupResult<S> resolveSchemaByGlobalId(long globalId) {
        return schemaCache.getByGlobalId(globalId, globalIdKey -> {
            if (resolveDereferenced) {
                return resolveSchemaDereferenced(globalIdKey);
            } else {
                return resolveSchemaWithReferences(globalIdKey);
            }
        });
    }

    private SchemaLookupResult<S> resolveSchemaDereferenced(long globalId) {
        String rawSchema = this.sdk.getSchemaByGlobalId(globalId, true);

        byte[] schema = rawSchema.getBytes(StandardCharsets.UTF_8);
        S parsed = schemaParser.parseSchema(schema, Collections.emptyMap());

        ParsedSchemaImpl<S> ps = new ParsedSchemaImpl<S>().setParsedSchema(parsed).setRawSchema(schema);

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

        return result.globalId(globalId).parsedSchema(ps).build();
    }

    private SchemaLookupResult<S> resolveSchemaWithReferences(long globalId) {
        String rawSchema = this.sdk.getSchemaByGlobalId(globalId, false);

        // Get the artifact references
        final List<RegistryArtifactReference> artifactReferences = this.sdk.getReferencesByGlobalId(globalId);
        // If there are any references for the schema being parsed, resolve them before parsing the schema
        final Map<String, ParsedSchema<S>> resolvedReferences = resolveReferences(artifactReferences);

        byte[] schema = rawSchema.getBytes(StandardCharsets.UTF_8);
        S parsed = schemaParser.parseSchema(schema, resolvedReferences);

        ParsedSchemaImpl<S> ps = new ParsedSchemaImpl<S>().setParsedSchema(parsed)
                .setSchemaReferences(new ArrayList<>(resolvedReferences.values())).setRawSchema(schema);

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

        return result.globalId(globalId).parsedSchema(ps).build();
    }

    protected Map<String, ParsedSchema<S>> resolveReferences(List<RegistryArtifactReference> artifactReferences) {
        Map<String, ParsedSchema<S>> resolvedReferences = new HashMap<>();

        artifactReferences.forEach(reference -> {
            String groupId = reference.getGroupId() == null ? "default" : reference.getGroupId();
            String artifactId = reference.getArtifactId();
            String version = reference.getVersion();

            final String referenceContent = this.sdk.getSchemaByGAV(groupId, artifactId, version);
            final List<RegistryArtifactReference> referenceReferences =
                    this.sdk.getReferencesByGAV(groupId, artifactId, version);

            if (!referenceReferences.isEmpty()) {
                final Map<String, ParsedSchema<S>> nestedReferences = resolveReferences(referenceReferences);
                resolvedReferences.putAll(nestedReferences);
                resolvedReferences.put(reference.getName(), parseSchemaFromStream(reference.getName(),
                        referenceContent, resolveReferences(referenceReferences)));
            } else {
                resolvedReferences.put(reference.getName(),
                        parseSchemaFromStream(reference.getName(), referenceContent, Collections.emptyMap()));
            }
        });
        return resolvedReferences;
    }

    private ParsedSchema<S> parseSchemaFromStream(String name, String rawSchema,
            Map<String, ParsedSchema<S>> resolvedReferences) {
        byte[] schema = rawSchema.getBytes(StandardCharsets.UTF_8);
        S parsed = schemaParser.parseSchema(schema, resolvedReferences);
        return new ParsedSchemaImpl<S>().setParsedSchema(parsed)
                .setSchemaReferences(new ArrayList<>(resolvedReferences.values())).setReferenceName(name)
                .setRawSchema(schema);
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#reset()
     */
    @Override
    public void reset() {
        this.schemaCache.clear();
    }

    /**
     * @see java.io.Closeable#close()
     */
    @Override
    public void close() throws IOException {
        try {
            this.sdk.close();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    protected void loadFromVersionCoordinates(RegistryVersionCoordinates version,
                                           SchemaLookupResult.SchemaLookupResultBuilder<S> resultBuilder) {
        resultBuilder.globalId(version.getGlobalId());
        resultBuilder.contentId(version.getContentId());
        resultBuilder.groupId(version.getGroupId());
        resultBuilder.artifactId(version.getArtifactId());
        resultBuilder.version(String.valueOf(version.getVersion()));
    }
}
