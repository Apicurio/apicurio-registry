package io.apicurio.registry.resolver;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.resolver.config.DefaultSchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.utils.IoUtil;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static io.apicurio.registry.client.auth.VertXAuthFactory.buildSimpleAuthWebClient;

/**
 * Base implementation of {@link SchemaResolver}
 *
 */
public abstract class AbstractSchemaResolver<S, T> implements SchemaResolver<S, T> {

    protected final ERCache<SchemaLookupResult<S>> schemaCache = new ERCache<>();

    protected DefaultSchemaResolverConfig config;
    protected SchemaParser<S, T> schemaParser;
    protected RegistryClient client;
    protected ArtifactReferenceResolverStrategy<S, T> artifactResolverStrategy;

    protected String explicitArtifactGroupId;
    protected String explicitArtifactId;
    protected String explicitArtifactVersion;

    @Override
    public void configure(Map<String, ?> configs, SchemaParser<S, T> schemaParser) {
        this.schemaParser = schemaParser;
        this.config = new DefaultSchemaResolverConfig(configs);
        if (client == null) {
            String baseUrl = config.getRegistryUrl();
            if (baseUrl == null) {
                throw new IllegalArgumentException("Missing registry base url, set " + SchemaResolverConfig.REGISTRY_URL);
            }

            String authServerURL = config.getAuthServiceUrl();
            String tokenEndpoint = config.getTokenEndpoint();

            try {
                if (authServerURL != null || tokenEndpoint != null) {
                    client = configureClientWithBearerAuthentication(config, baseUrl, authServerURL, tokenEndpoint);
                } else {
                    String username = config.getAuthUsername();

                    if (username != null) {
                        client = configureClientWithBasicAuth(config, baseUrl, username);
                    } else {
                        var adapter = new VertXRequestAdapter(Vertx.vertx());
                        adapter.setBaseUrl(baseUrl);
                        client = new RegistryClient(adapter);
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        Object ais = config.getArtifactResolverStrategy();
        Utils.instantiate(ArtifactReferenceResolverStrategy.class, ais, this::setArtifactResolverStrategy);

        schemaCache.configureLifetime(config.getCheckPeriod());
        schemaCache.configureRetryBackoff(config.getRetryBackoff());
        schemaCache.configureRetryCount(config.getRetryCount());
        schemaCache.configureCacheLatest(config.getCacheLatest());
        schemaCache.configureFaultTolerantRefresh(config.getFaultTolerantRefresh());

        schemaCache.configureGlobalIdKeyExtractor(SchemaLookupResult::getGlobalId);
        schemaCache.configureContentKeyExtractor(schema -> Optional.ofNullable(schema.getParsedSchema().getRawSchema()).map(IoUtil::toString).orElse(null));
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
    }

    /**
     * @param client the client to set
     */
    @Override
    public void setClient(RegistryClient client) {
        this.client = client;
    }

    /**
     * @param artifactResolverStrategy the artifactResolverStrategy to set
     */
    @Override
    public void setArtifactResolverStrategy(ArtifactReferenceResolverStrategy<S, T> artifactResolverStrategy) {
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
     * Resolve an artifact reference for the given record, and optional parsed schema.  This will use
     * the artifact resolver strategy and then override the values from that strategy with any explicitly configured
     * values (groupId, artifactId, version).
     * @param data
     * @param parsedSchema
     * @param isReference
     * @return artifact reference
     */
    protected ArtifactReference resolveArtifactReference(Record<T> data, ParsedSchema<S> parsedSchema, boolean isReference, String referenceArtifactId) {
        ArtifactReference artifactReference = artifactResolverStrategy.artifactReference(data, parsedSchema);
        artifactReference = ArtifactReference.builder()
                .groupId(this.explicitArtifactGroupId == null ? artifactReference.getGroupId() : this.explicitArtifactGroupId)
                .artifactId(resolveArtifactId(artifactReference.getArtifactId(), isReference, referenceArtifactId))
                .version(this.explicitArtifactVersion == null ? artifactReference.getVersion() : this.explicitArtifactVersion)
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
            //TODO getContentByGlobalId have to return some minumum metadata (groupId, artifactId and version)
            //TODO or at least add some method to the api to return the version metadata by globalId
//            ArtifactMetaData artifactMetadata = client.getArtifactMetaData("TODO", artifactId);

            InputStream rawSchema = client.ids().globalIds().byGlobalId(globalId).get(config -> {
                    config.headers.add("CANONICAL", "false");
                    config.headers.add("DEREFERENCE", "true");
                });

                //Get the artifact references
                final List<io.apicurio.registry.rest.client.models.ArtifactReference> artifactReferences = client.ids().globalIds().byGlobalId(globalId).references().get();
                //If there are any references for the schema being parsed, resolve them before parsing the schema
                final Map<String, ParsedSchema<S>> resolvedReferences = resolveReferences(artifactReferences);

                byte[] schema = IoUtil.toBytes(rawSchema);
                S parsed = schemaParser.parseSchema(schema, resolvedReferences);

            ParsedSchemaImpl<S> ps = new ParsedSchemaImpl<S>()
                        .setParsedSchema(parsed)
                        .setSchemaReferences(new ArrayList<>(resolvedReferences.values()))
                        .setRawSchema(schema);

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            return result
                //FIXME it's impossible to retrieve this info with only the globalId
//                  .groupId(null)
//                  .artifactId(null)
//                  .version(0)
                .globalId(globalIdKey)
                .parsedSchema(ps)
                .build();
        });
    }

    protected Map<String, ParsedSchema<S>> resolveReferences(List<io.apicurio.registry.rest.client.models.ArtifactReference> artifactReferences) {
        Map<String, ParsedSchema<S>> resolvedReferences = new HashMap<>();
        artifactReferences.forEach(reference -> {
            final InputStream referenceContent = client.groups().byGroupId(reference.getGroupId() == null ? "default" : reference.getGroupId()).artifacts().byArtifactId(reference.getArtifactId()).versions().byVersion(reference.getVersion()).get();
            final List<io.apicurio.registry.rest.client.models.ArtifactReference> referenceReferences = client
                .groups()
                .byGroupId(reference.getGroupId() == null ? "default" : reference.getGroupId()) // TODO verify the old logic: .pathParams(List.of(groupId == null ? "null" : groupId, artifactId, version)) GroupRequestsProvider.java
                .artifacts()
                .byArtifactId(reference.getArtifactId())
                .versions()
                .byVersion(reference.getVersion())
                .references()
                .get();

            if (!referenceReferences.isEmpty()) {
                final Map<String, ParsedSchema<S>> nestedReferences = resolveReferences(referenceReferences);
                resolvedReferences.putAll(nestedReferences);
                resolvedReferences.put(reference.getName(), parseSchemaFromStream(reference.getName(), referenceContent, resolveReferences(referenceReferences)));
            } else {
                resolvedReferences.put(reference.getName(), parseSchemaFromStream(reference.getName(), referenceContent, Collections.emptyMap()));
            }
        });
        return resolvedReferences;
    }

    private ParsedSchema<S> parseSchemaFromStream(String name, InputStream rawSchema, Map<String, ParsedSchema<S>> resolvedReferences) {
        byte[] schema = IoUtil.toBytes(rawSchema);
        S parsed = schemaParser.parseSchema(schema, resolvedReferences);
        return new ParsedSchemaImpl<S>()
                .setParsedSchema(parsed)
                .setSchemaReferences(new ArrayList<>(resolvedReferences.values()))
                .setReferenceName(name)
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
    }

    private RegistryClient configureClientWithBearerAuthentication(DefaultSchemaResolverConfig config, String registryUrl, String authServerUrl, String tokenEndpoint) {
        RequestAdapter auth;
        if (authServerUrl != null) {
            auth = configureAuthWithRealm(config, authServerUrl);
        } else {
            auth = configureAuthWithUrl(config, tokenEndpoint);
        }
        auth.setBaseUrl(registryUrl);
        return new RegistryClient(auth);
    }

    private RequestAdapter configureAuthWithRealm(DefaultSchemaResolverConfig config, String authServerUrl) {
        final String realm = config.getAuthRealm();

        if (realm == null) {
            throw new IllegalArgumentException("Missing registry auth realm, set " + SchemaResolverConfig.AUTH_REALM);
        }

        final String tokenEndpoint =  authServerUrl + String.format(SchemaResolverConfig.AUTH_SERVICE_URL_TOKEN_ENDPOINT, realm);

        return configureAuthWithUrl(config, tokenEndpoint);
    }

    private RequestAdapter configureAuthWithUrl(DefaultSchemaResolverConfig config, String tokenEndpoint) {
        final String clientId = config.getAuthClientId();

        if (clientId == null) {
            throw new IllegalArgumentException("Missing registry auth clientId, set " + SchemaResolverConfig.AUTH_CLIENT_ID);
        }
        final String clientSecret = config.getAuthClientSecret();

        if (clientSecret == null) {
            throw new IllegalArgumentException("Missing registry auth secret, set " + SchemaResolverConfig.AUTH_CLIENT_SECRET);
        }

        final String clientScope = config.getAuthClientScope();

        RequestAdapter adapter = new VertXRequestAdapter(buildOIDCWebClient(tokenEndpoint, clientId, clientSecret, clientScope));
        return adapter;
    }

    private RegistryClient configureClientWithBasicAuth(DefaultSchemaResolverConfig config, String registryUrl, String username) {

        final String password = config.getAuthPassword();

        if (password == null) {
            throw new IllegalArgumentException("Missing registry auth password, set " + SchemaResolverConfig.AUTH_PASSWORD);
        }

        var adapter = new VertXRequestAdapter(buildSimpleAuthWebClient(username, password));

        adapter.setBaseUrl(registryUrl);
        return new RegistryClient(adapter);
    }

    protected void loadFromArtifactMetaData(ArtifactMetaData artifactMetadata, SchemaLookupResult.SchemaLookupResultBuilder<S> resultBuilder) {
        resultBuilder.globalId(artifactMetadata.getGlobalId());
        resultBuilder.contentId(artifactMetadata.getContentId());
        resultBuilder.groupId(artifactMetadata.getGroupId());
        resultBuilder.artifactId(artifactMetadata.getId());
        resultBuilder.version(String.valueOf(artifactMetadata.getVersion()));
    }

    protected void loadFromArtifactMetaData(VersionMetaData artifactMetadata, SchemaLookupResult.SchemaLookupResultBuilder<S> resultBuilder) {
        resultBuilder.globalId(artifactMetadata.getGlobalId());
        resultBuilder.contentId(artifactMetadata.getContentId());
        resultBuilder.groupId(artifactMetadata.getGroupId());
        resultBuilder.artifactId(artifactMetadata.getId());
        resultBuilder.version(String.valueOf(artifactMetadata.getVersion()));
    }
}
