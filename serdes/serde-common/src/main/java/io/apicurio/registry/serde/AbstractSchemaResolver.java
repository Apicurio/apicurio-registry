package io.apicurio.registry.serde;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.resolver.ERCache;
import io.apicurio.registry.resolver.ParsedSchemaImpl;
import io.apicurio.registry.resolver.config.DefaultSchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.resolver.utils.Utils;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.data.KafkaSerdeMetadata;
import io.apicurio.registry.serde.data.KafkaSerdeRecord;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.utils.IoUtil;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static io.apicurio.registry.client.auth.VertXAuthFactory.buildSimpleAuthWebClient;

/**
 * This class is deprecated, it's recommended to migrate to the new implementation at
 * {@link io.apicurio.registry.resolver.AbstractSchemaResolver} Base implementation of {@link SchemaResolver}
 */
@Deprecated
public abstract class AbstractSchemaResolver<S, T> implements SchemaResolver<S, T> {

    protected final ERCache<SchemaLookupResult<S>> schemaCache = new ERCache<>();

    protected io.apicurio.registry.resolver.SchemaParser<S, T> schemaParser;
    protected RegistryClient client;
    protected boolean isKey;
    protected ArtifactReferenceResolverStrategy<S, T> artifactResolverStrategy;

    protected String explicitArtifactGroupId;
    protected String explicitArtifactId;
    protected String explicitArtifactVersion;

    protected Vertx vertx;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public void configure(Map<String, ?> configs,
            io.apicurio.registry.resolver.SchemaParser<S, T> schemaMapper) {
        this.schemaParser = schemaMapper;

        if (this.vertx == null) {
            this.vertx = VertXAuthFactory.defaultVertx;
        }

        Object isKeyFromConfig = configs.get(SerdeConfig.IS_KEY);
        // is key have to come always, we set it
        configure(configs, (Boolean) isKeyFromConfig, new SchemaParser() {

            /**
             * @see io.apicurio.registry.serde.SchemaParser#artifactType()
             */
            @Override
            public String artifactType() {
                return schemaMapper.artifactType();
            }

            /**
             * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
             */
            @Override
            public Object parseSchema(byte[] rawSchema) {
                // Empty map passed as references. References are not supported when using this class.
                return schemaMapper.parseSchema(rawSchema, Collections.emptyMap());
            }

        });
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#configure(java.util.Map, boolean,
     *      io.apicurio.registry.serde.SchemaParser)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey, SchemaParser<S> schemaParser) {
        this.isKey = isKey;

        if (this.vertx == null) {
            this.vertx = VertXAuthFactory.defaultVertx;
        }

        DefaultSchemaResolverConfig config = new DefaultSchemaResolverConfig(configs);
        if (client == null) {
            String baseUrl = config.getRegistryUrl();
            if (baseUrl == null) {
                throw new IllegalArgumentException(
                        "Missing registry base url, set " + SerdeConfig.REGISTRY_URL);
            }

            String authServerURL = config.getAuthServiceUrl();
            String tokenEndpoint = config.getTokenEndpoint();

            try {
                if (authServerURL != null || tokenEndpoint != null) {
                    client = configureClientWithBearerAuthentication(config, baseUrl, authServerURL,
                            tokenEndpoint);
                } else {
                    String username = config.getAuthUsername();

                    if (username != null) {
                        client = configureClientWithBasicAuth(config, baseUrl, username);
                    } else {
                        RequestAdapter adapter = new VertXRequestAdapter(this.vertx);
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

        schemaCache.configureGlobalIdKeyExtractor(SchemaLookupResult::getGlobalId);
        schemaCache.configureContentKeyExtractor(
                schema -> Optional.ofNullable(schema.getRawSchema()).map(IoUtil::toString).orElse(null));
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
    public void setArtifactResolverStrategy(
            ArtifactReferenceResolverStrategy<S, T> artifactResolverStrategy) {
        this.artifactResolverStrategy = artifactResolverStrategy;
    }

    /**
     * @param isKey the isKey to set
     */
    public void setIsKey(boolean isKey) {
        this.isKey = isKey;
    }

    /**
     * @see io.apicurio.registry.resolver.SchemaResolver#getSchemaParser()
     */
    @Override
    public io.apicurio.registry.resolver.SchemaParser<S, T> getSchemaParser() {
        return this.schemaParser;
    }

    /**
     * Resolve an artifact reference given the topic name, message headers, data, and optional parsed schema.
     * This will use the artifact resolver strategy and then override the values from that strategy with any
     * explicitly configured values (groupId, artifactId, version).
     *
     * @param topic
     * @param headers
     * @param data
     * @param parsedSchema
     */
    protected ArtifactReference resolveArtifactReference(String topic, T data, ParsedSchema<S> parsedSchema) {

        KafkaSerdeMetadata metadata = new KafkaSerdeMetadata(topic, isKey, null);
        KafkaSerdeRecord<T> record = new KafkaSerdeRecord<T>(metadata, data);

        io.apicurio.registry.resolver.ParsedSchema<S> ps = new ParsedSchemaImpl<S>()
                .setParsedSchema(parsedSchema.getParsedSchema()).setRawSchema(parsedSchema.getRawSchema());

        io.apicurio.registry.resolver.strategy.ArtifactReference artifactReference = artifactResolverStrategy
                .artifactReference(record, ps);

        return ArtifactReference.builder()
                .groupId(this.explicitArtifactGroupId == null ? artifactReference.getGroupId()
                    : this.explicitArtifactGroupId)
                .artifactId(this.explicitArtifactId == null ? artifactReference.getArtifactId()
                    : this.explicitArtifactId)
                .version(this.explicitArtifactVersion == null ? artifactReference.getVersion()
                    : this.explicitArtifactVersion)
                .build();
    }

    protected SchemaLookupResult<S> resolveSchemaByGlobalId(long globalId) {
        return schemaCache.getByGlobalId(globalId, globalIdKey -> {
            // TODO getContentByGlobalId have to return some minumum metadata (groupId, artifactId and
            // version)
            // TODO or at least add some method to the api to return the version metadata by globalId
            // ArtifactMetaData artifactMetadata = client.getArtifactMetaData("TODO", artifactId);
            InputStream rawSchema = client.ids().globalIds().byGlobalId(globalIdKey).get();

            byte[] schema = IoUtil.toBytes(rawSchema);
            S parsed = schemaParser.parseSchema(schema, Collections.emptyMap());

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            return result
                    // FIXME it's impossible to retrieve this info with only the globalId
                    // .groupId(null)
                    // .artifactId(null)
                    // .version(0)
                    .globalId(globalIdKey).rawSchema(schema).schema(parsed).build();
        });
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#reset()
     */
    @Override
    public void reset() {
        this.schemaCache.clear();
    }

    @Override
    public void close() throws IOException {
        if (this.vertx != null) {
            this.vertx.close();
        }
    }

    private RegistryClient configureClientWithBearerAuthentication(DefaultSchemaResolverConfig config,
            String registryUrl, String authServerUrl, String tokenEndpoint) {
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
            throw new IllegalArgumentException("Missing registry auth realm, set " + SerdeConfig.AUTH_REALM);
        }

        final String tokenEndpoint = authServerUrl
                + String.format(SerdeConfig.AUTH_SERVICE_URL_TOKEN_ENDPOINT, realm);

        return configureAuthWithUrl(config, tokenEndpoint);
    }

    private RequestAdapter configureAuthWithUrl(DefaultSchemaResolverConfig config, String tokenEndpoint) {
        final String clientId = config.getAuthClientId();

        if (clientId == null) {
            throw new IllegalArgumentException(
                    "Missing registry auth clientId, set " + SerdeConfig.AUTH_CLIENT_ID);
        }
        final String clientSecret = config.getAuthClientSecret();

        if (clientSecret == null) {
            throw new IllegalArgumentException(
                    "Missing registry auth secret, set " + SerdeConfig.AUTH_CLIENT_SECRET);
        }

        RequestAdapter adapter = new VertXRequestAdapter(
                buildOIDCWebClient(this.vertx, tokenEndpoint, clientId, clientSecret));
        return adapter;
    }

    private RegistryClient configureClientWithBasicAuth(DefaultSchemaResolverConfig config,
            String registryUrl, String username) {

        final String password = config.getAuthPassword();

        if (password == null) {
            throw new IllegalArgumentException(
                    "Missing registry auth password, set " + SerdeConfig.AUTH_PASSWORD);
        }

        var adapter = new VertXRequestAdapter(buildSimpleAuthWebClient(this.vertx, username, password));

        adapter.setBaseUrl(registryUrl);
        return new RegistryClient(adapter);
    }

    protected void loadFromArtifactMetaData(VersionMetaData artifactMetadata,
            SchemaLookupResult.SchemaLookupResultBuilder<S> resultBuilder) {
        resultBuilder.globalId(artifactMetadata.getGlobalId());
        resultBuilder.contentId(artifactMetadata.getContentId());
        resultBuilder.groupId(artifactMetadata.getGroupId());
        resultBuilder.artifactId(artifactMetadata.getArtifactId());
        resultBuilder.version(String.valueOf(artifactMetadata.getVersion()));
    }
}
