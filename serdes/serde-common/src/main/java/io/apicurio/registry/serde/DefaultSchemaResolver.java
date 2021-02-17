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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.auth.KeycloakAuth;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.serde.strategy.ArtifactReference;
import io.apicurio.registry.serde.strategy.ArtifactResolverStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.serde.utils.Utils;
import io.apicurio.registry.utils.IoUtil;

/**
 * Default implemntation of {@link SchemaResolver}
 *
 * @author Fabian Martinez
 */
public class DefaultSchemaResolver<S, T> implements SchemaResolver<S, T>{

    //TODO improve cache and add refresh period
    private final Map<Long, SchemaLookupResult<S>> schemasCache = new ConcurrentHashMap<>();
    private final Map<String, Long> globalIdCacheByContent = new ConcurrentHashMap<>();
    private CheckPeriodCache<ArtifactReference, Long> globalIdCacheByArtifactReference = new CheckPeriodCache<>(0);

    private SchemaParser<S> schemaParser;
    private RegistryClient client;
    private boolean isKey;
    private ArtifactResolverStrategy<S> artifactResolverStrategy;

    private boolean autoCreateArtifact = false;
    private IfExists autoCreateBehavior = IfExists.RETURN_OR_UPDATE;
//    private boolean useLatestArtifact;

    private String artifactGroupId;

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#configure(java.util.Map, boolean, io.apicurio.registry.serde.SchemaParser)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey, SchemaParser<S> schemaParser) {
        this.schemaParser = schemaParser;
        this.isKey = isKey;
        if (client == null) {
            String baseUrl = (String) configs.get(SerdeConfigKeys.REGISTRY_URL);
            if (baseUrl == null) {
                throw new IllegalArgumentException("Missing registry base url, set " + SerdeConfigKeys.REGISTRY_URL);
            }

            String authServerURL = (String) configs.get(SerdeConfigKeys.AUTH_SERVICE_URL);

            try {
                if (authServerURL != null) {
                    client = configureClientWithAuthentication(configs, baseUrl, authServerURL);
                } else {
                    client = RegistryClientFactory.create(baseUrl);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        Object ais = configs.get(SerdeConfigKeys.ARTIFACT_ID_STRATEGY);
        if (ais == null) {
            if (this.artifactResolverStrategy == null) {
                this.setArtifactResolverStrategy(new TopicIdStrategy<>());
            }
        } else {
            Utils.instantiate(ArtifactResolverStrategy.class, ais, this::setArtifactResolverStrategy);
        }

        this.autoCreateArtifact = Utils.isTrue(configs.get(SerdeConfigKeys.AUTO_REGISTER_ARTIFACT));
        String createArtifactBehavior = (String) configs.get(SerdeConfigKeys.AUTO_REGISTER_ARTIFACT_BEHAVIOR);
        if (createArtifactBehavior != null) {
            this.autoCreateArtifact = true;
            this.autoCreateBehavior = IfExists.fromValue(createArtifactBehavior);
        }

        String groupIdOverride = (String) configs.get(SerdeConfigKeys.ARTIFACT_GROUP_ID);
        if (groupIdOverride != null) {
            this.artifactGroupId = groupIdOverride;
        }

        long checkPeriod = 0;
        Object cp = configs.get(SerdeConfigKeys.CHECK_PERIOD_MS);
        if (cp != null) {
            long checkPeriodParam;
            if (cp instanceof Number) {
                checkPeriodParam = ((Number) cp).longValue();
            } else if (cp instanceof String) {
                checkPeriodParam = Long.parseLong((String) cp);
            } else if (cp instanceof Duration) {
                checkPeriodParam = ((Duration) cp).toMillis();
            } else {
                throw new IllegalArgumentException("Check period config param type unsupported (must be a Number, String, or Duration): " + cp);
            }
            if (checkPeriodParam < 0) {
                throw new IllegalArgumentException("Check period must be non-negative: " + checkPeriodParam);
            }
            checkPeriod = checkPeriodParam;
        }
        globalIdCacheByArtifactReference = new CheckPeriodCache<>(checkPeriod);
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
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchema(java.lang.String, org.apache.kafka.common.header.Headers, java.lang.Object, io.apicurio.registry.serde.ParsedSchema)
     */
    @Override
    public SchemaLookupResult<S> resolveSchema(String topic, Headers headers, T data, Optional<ParsedSchema<S>> parsedSchema) {

        final ArtifactReference artifactReference = resolveArtifactReference(topic, headers, data, parsedSchema);

        {
            Long globalId = globalIdCacheByArtifactReference.get(artifactReference);
            if (globalId != null) {
                SchemaLookupResult<S> schema = schemasCache.get(globalId);
                if (schema != null) {
                    return schema;
                }
            }
        }


        if (autoCreateArtifact && parsedSchema.isPresent()) {

            byte[] rawSchema = parsedSchema.get().getRawSchema();
            String rawSchemaString = IoUtil.toString(rawSchema);

            Long globalId = globalIdCacheByContent.computeIfAbsent(rawSchemaString, key -> {
                ArtifactMetaData artifactMetadata = client.createArtifact(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion(), schemaParser.artifactType(), this.autoCreateBehavior, false, IoUtil.toStream(rawSchema));

                SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

                loadFromArtifactMetaData(artifactMetadata, result);

                S schema = parsedSchema.get().getParsedSchema();
                result.rawSchema(rawSchema);
                result.schema(schema);

                Long newGlobalId = artifactMetadata.getGlobalId();
                schemasCache.put(newGlobalId, result.build());
                globalIdCacheByArtifactReference.put(artifactReference, newGlobalId);
                return newGlobalId;
            });

            return schemasCache.get(globalId);
        } else if (parsedSchema.isPresent()) {
            //compatibilty with FindBySchemaIdStrategy

            byte[] rawSchema = parsedSchema.get().getRawSchema();
            String rawSchemaString = IoUtil.toString(rawSchema);

            Long globalId = globalIdCacheByContent.computeIfAbsent(rawSchemaString, key -> {
                VersionMetaData artifactMetadata = client.getArtifactVersionMetaDataByContent(artifactReference.getGroupId(), artifactReference.getArtifactId(), true, IoUtil.toStream(rawSchema));

                SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

                loadFromArtifactMetaData(artifactMetadata, result);

                S schema = parsedSchema.get().getParsedSchema();
                result.rawSchema(rawSchema);
                result.schema(schema);

                Long artifactGlobalId = artifactMetadata.getGlobalId();
                schemasCache.put(artifactGlobalId, result.build());
                globalIdCacheByArtifactReference.put(artifactReference, artifactGlobalId);
                return artifactGlobalId;
            });

            return schemasCache.get(globalId);
        }
        return resolveSchemaByCoordinates(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion());
    }

    private ArtifactReference resolveArtifactReference(String topic, Headers headers, T data, Optional<ParsedSchema<S>> parsedSchema) {
        ArtifactReference artifactReference = artifactResolverStrategy.artifactReference(topic, isKey, parsedSchema.map(ParsedSchema<S>::getParsedSchema).orElse(null));
        //TODO implement override reference values by config properties here
        if (this.artifactGroupId != null) {
            artifactReference = ArtifactReference.builder()
                    .groupId(this.artifactGroupId)
                    .artifactId(artifactReference.getArtifactId())
                    .version(artifactReference.getVersion())
                    .build();
        }
        return artifactReference;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchemaByGlobalId(long)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByGlobalId(long globalId) {

        return schemasCache.computeIfAbsent(globalId, k -> {
            //TODO getContentByGlobalId have to return some minumum metadata (groupId, artifactId and version)
            //TODO or at least add some methd to the api to return the version metadata by globalId
//            ArtifactMetaData artifactMetadata = client.getArtifactMetaData("TODO", artifactId);
            InputStream rawSchema = client.getContentByGlobalId(globalId);

            byte[] schema = IoUtil.toBytes(rawSchema);
            S parsed = schemaParser.parseSchema(schema);

          SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

          return result
              //FIXME it's impossible to retrieve this info with only the globalId
//                  .groupId(null)
//                  .artifactId(null)
//                  .version(0)
                  .globalId(globalId)
                  .rawSchema(schema)
                  .schema(parsed)
                  .build();

        });

    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchemaByCoordinates(java.lang.String, java.lang.String, int)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByCoordinates(String groupId, String artifactId, String version) {

        ArtifactReference reference = ArtifactReference.builder().groupId(groupId).artifactId(artifactId).version(version).build();

        Long globalId = globalIdCacheByArtifactReference.compute(reference,
                artifactReference -> {
                    SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();
                    //TODO if getArtifactVersion returns the artifact version and globalid in the headers we can reduce this to only one http call
                    Long gid;
                    if (version == null) {
                        var metadata = client.getArtifactMetaData(groupId, artifactId);
                        loadFromArtifactMetaData(metadata, result);
                        gid = metadata.getGlobalId();
                    } else {
                        var metadata = client.getArtifactVersionMetaData(groupId, artifactId, version);
                        loadFromArtifactMetaData(metadata, result);
                        gid = metadata.getGlobalId();
                    }

                    InputStream rawSchema = client.getContentByGlobalId(gid);

                    byte[] schema = IoUtil.toBytes(rawSchema);
                    S parsed = schemaParser.parseSchema(schema);

                    result
                        .rawSchema(schema)
                        .schema(parsed);

                    schemasCache.put(gid, result.build());
                    globalIdCacheByContent.put(IoUtil.toString(schema), gid);
                    return gid;
                });

        return schemasCache.get(globalId);
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#reset()
     */
    @Override
    public void reset() {
        this.schemasCache.clear();
        this.globalIdCacheByContent.clear();
        this.globalIdCacheByArtifactReference.clear();
    }

    private RegistryClient configureClientWithAuthentication(Map<String, ?> configs, String registryUrl, String authServerUrl) {

        final String realm = (String) configs.get(SerdeConfigKeys.AUTH_REALM);

        if (realm == null) {
            throw new IllegalArgumentException("Missing registry auth realm, set " + SerdeConfigKeys.AUTH_REALM);
        }
        final String clientId = (String) configs.get(SerdeConfigKeys.AUTH_CLIENT_ID);

        if (clientId == null) {
            throw new IllegalArgumentException("Missing registry auth clientId, set " + SerdeConfigKeys.AUTH_CLIENT_ID);
        }
        final String clientSecret = (String) configs.get(SerdeConfigKeys.AUTH_CLIENT_SECRET);

        if (clientSecret == null) {
            throw new IllegalArgumentException("Missing registry auth secret, set " + SerdeConfigKeys.AUTH_CLIENT_SECRET);
        }

        Auth auth = new KeycloakAuth(authServerUrl, realm, clientId, clientSecret);

        return RegistryClientFactory.create(registryUrl, auth);
    }

    private void loadFromArtifactMetaData(ArtifactMetaData artifactMetadata, SchemaLookupResult.SchemaLookupResultBuilder<S> resultBuilder) {
        resultBuilder.globalId(artifactMetadata.getGlobalId());
        resultBuilder.groupId(artifactMetadata.getGroupId());
        resultBuilder.artifactId(artifactMetadata.getId());
        resultBuilder.version(String.valueOf(artifactMetadata.getVersion()));
    }

    private void loadFromArtifactMetaData(VersionMetaData artifactMetadata, SchemaLookupResult.SchemaLookupResultBuilder<S> resultBuilder) {
        resultBuilder.globalId(artifactMetadata.getGlobalId());
        resultBuilder.groupId(artifactMetadata.getGroupId());
        resultBuilder.artifactId(artifactMetadata.getId());
        resultBuilder.version(String.valueOf(artifactMetadata.getVersion()));
    }
}
