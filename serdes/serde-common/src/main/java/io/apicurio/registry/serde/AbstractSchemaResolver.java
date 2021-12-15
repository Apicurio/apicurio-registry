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

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.serde.config.DefaultSchemaResolverConfig;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.serde.strategy.ArtifactResolverStrategy;
import io.apicurio.registry.serde.utils.Utils;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.rest.client.auth.Auth;
import io.apicurio.rest.client.auth.BasicAuth;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import org.apache.kafka.common.header.Headers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

/**
 * Default implemntation of {@link SchemaResolver}
 *
 * @author Fabian Martinez
 * @author Jakub Senko <jsenko@redhat.com>
 */
public abstract class AbstractSchemaResolver<S, T> implements SchemaResolver<S, T> {

    protected final ERCache<SchemaLookupResult<S>> schemaCache = new ERCache<>();

    protected SchemaParser<S> schemaParser;
    protected RegistryClient client;
    protected ApicurioHttpClient authClient;
    protected boolean isKey;
    protected ArtifactResolverStrategy<S> artifactResolverStrategy;

    protected String explicitArtifactGroupId;
    protected String explicitArtifactId;
    protected String explicitArtifactVersion;

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#configure(java.util.Map, boolean, io.apicurio.registry.serde.SchemaParser)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey, SchemaParser<S> schemaParser) {
        this.schemaParser = schemaParser;
        this.isKey = isKey;
        DefaultSchemaResolverConfig config = new DefaultSchemaResolverConfig(configs);
        if (client == null) {
            String baseUrl = config.getRegistryUrl();
            if (baseUrl == null) {
                throw new IllegalArgumentException("Missing registry base url, set " + SerdeConfig.REGISTRY_URL);
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
                        client = RegistryClientFactory.create(baseUrl, config.originals());
                    }
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        Object ais = config.getArtifactResolverStrategy();
        Utils.instantiate(ArtifactResolverStrategy.class, ais, this::setArtifactResolverStrategy);

        schemaCache.configureLifetime(config.getCheckPeriod());
        schemaCache.configureRetryBackoff(config.getRetryBackoff());
        schemaCache.configureRetryCount(config.getRetryCount());

        schemaCache.configureArtifactReferenceKeyExtractor(SchemaLookupResult::toArtifactReference);
        schemaCache.configureGlobalIdKeyExtractor(SchemaLookupResult::getGlobalId);
        schemaCache.configureContentKeyExtractor(schema -> Optional.ofNullable(schema.getRawSchema()).map(IoUtil::toString).orElse(null));
        schemaCache.configureContentIdKeyExtractor(SchemaLookupResult::getContentId);
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
    public void setArtifactResolverStrategy(ArtifactResolverStrategy<S> artifactResolverStrategy) {
        this.artifactResolverStrategy = artifactResolverStrategy;
    }

    /**
     * @param isKey the isKey to set
     */
    public void setIsKey(boolean isKey) {
        this.isKey = isKey;
    }

    /**
     * Resolve an artifact reference given the topic name, message headers, data, and optional parsed schema.  This will use
     * the artifact resolver strategy and then override the values from that strategy with any explicitly configured
     * values (groupId, artifactId, version).
     * @param topic
     * @param headers
     * @param data
     * @param parsedSchema
     */
    protected ArtifactReference resolveArtifactReference(String topic, Headers headers, T data, ParsedSchema<S> parsedSchema) {

        S schema = null;
        if (artifactResolverStrategy.loadSchema() && parsedSchema != null) {
            schema = parsedSchema.getParsedSchema();
        }

        ArtifactReference artifactReference = artifactResolverStrategy.artifactReference(topic, isKey, schema);
        artifactReference = ArtifactReference.builder()
                .groupId(this.explicitArtifactGroupId == null ? artifactReference.getGroupId() : this.explicitArtifactGroupId)
                .artifactId(this.explicitArtifactId == null ? artifactReference.getArtifactId() : this.explicitArtifactId)
                .version(this.explicitArtifactVersion == null ? artifactReference.getVersion() : this.explicitArtifactVersion)
                .build();
        return artifactReference;
    }

    protected SchemaLookupResult<S> resolveSchemaByGlobalId(long globalId) {
        return schemaCache.getByGlobalId(globalId, globalIdKey -> {
            //TODO getContentByGlobalId have to return some minumum metadata (groupId, artifactId and version)
            //TODO or at least add some method to the api to return the version metadata by globalId
//            ArtifactMetaData artifactMetadata = client.getArtifactMetaData("TODO", artifactId);
            InputStream rawSchema = client.getContentByGlobalId(globalIdKey);

            byte[] schema = IoUtil.toBytes(rawSchema);
            S parsed = schemaParser.parseSchema(schema);

            SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

            return result
                //FIXME it's impossible to retrieve this info with only the globalId
//                  .groupId(null)
//                  .artifactId(null)
//                  .version(0)
                .globalId(globalIdKey)
                .rawSchema(schema)
                .schema(parsed)
                .build();
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
        if (this.client != null) {
            this.client.close();
        }
        if (this.authClient != null) {
            this.authClient.close();
        }
    }

    private RegistryClient configureClientWithBearerAuthentication(DefaultSchemaResolverConfig config, String registryUrl, String authServerUrl, String tokenEndpoint) {
        Auth auth;
        if (authServerUrl != null) {
            auth = configureAuthWithRealm(config, authServerUrl);
        } else {
            auth = configureAuthWithUrl(config, tokenEndpoint);
        }
        return RegistryClientFactory.create(registryUrl, config.originals(), auth);
    }

    private OidcAuth configureAuthWithRealm(DefaultSchemaResolverConfig config, String authServerUrl) {
        final String realm = config.getAuthRealm();

        if (realm == null) {
            throw new IllegalArgumentException("Missing registry auth realm, set " + SerdeConfig.AUTH_REALM);
        }

        final String tokenEndpoint =  authServerUrl + String.format(SerdeConfig.AUTH_SERVICE_URL_TOKEN_ENDPOINT, realm);

        return configureAuthWithUrl(config, tokenEndpoint);
    }

    private OidcAuth configureAuthWithUrl(DefaultSchemaResolverConfig config, String tokenEndpoint) {
        final String clientId = config.getAuthClientId();

        if (clientId == null) {
            throw new IllegalArgumentException("Missing registry auth clientId, set " + SerdeConfig.AUTH_CLIENT_ID);
        }
        final String clientSecret = config.getAuthClientSecret();

        if (clientSecret == null) {
            throw new IllegalArgumentException("Missing registry auth secret, set " + SerdeConfig.AUTH_CLIENT_SECRET);
        }

        authClient = ApicurioHttpClientFactory.create(tokenEndpoint, new AuthErrorHandler());
        return new OidcAuth(authClient, clientId, clientSecret);
    }

    private RegistryClient configureClientWithBasicAuth(DefaultSchemaResolverConfig config, String registryUrl, String username) {

        final String password = config.getAuthPassword();

        if (password == null) {
            throw new IllegalArgumentException("Missing registry auth password, set " + SerdeConfig.AUTH_PASSWORD);
        }

        Auth auth = new BasicAuth(username, password);

        return RegistryClientFactory.create(registryUrl, config.originals(), auth);
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
