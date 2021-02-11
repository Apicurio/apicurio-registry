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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.common.header.Headers;

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
    private final Map<Long, ParsedSchema<S>> schemasCache = new ConcurrentHashMap<>();
    private final Map<String, Long> globalIdCacheByContent = new ConcurrentHashMap<>();
    private final Map<ArtifactReference, Long> globalIdCacheByArtifactReference = new ConcurrentHashMap<>();

    private SchemaParser<S> schemaParser;
    private RegistryClient client;
    private boolean isKey;
    private ArtifactResolverStrategy<S> artifactResolverStrategy;

    private boolean autoCreateArtifact;
    private IfExists autoCreateBehavior;
//    private boolean useLatestArtifact;

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
//                    client = configureClientWithAuthentication(configs, baseUrl, authServerURL);
                } else {
                    client = RegistryClientFactory.create(baseUrl);
//                    client = RegistryClientFactory.create(baseUrl, new HashMap<>(configs));
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

        String createArtifactBehavior = (String) configs.get(SerdeConfigKeys.CREATE_ARTIFACT_BEHAVIOR);
        if (createArtifactBehavior != null) {
            this.autoCreateArtifact = true;
            this.autoCreateBehavior = IfExists.fromValue(createArtifactBehavior);
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
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchema(java.lang.String, org.apache.kafka.common.header.Headers, java.lang.Object, io.apicurio.registry.serde.ParsedSchema)
     */
    @Override
    public SchemaLookupResult<S> resolveSchema(String topic, Headers headers, T data, Optional<ParsedSchema<S>> parsedSchema) {

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

        final ArtifactReference artifactReference = artifactResolverStrategy.artifactReference(topic, isKey, parsedSchema.map(ParsedSchema<S>::getParsedSchema).orElse(null));
        //TODO implement override reference values by config properties here

        {
            Long globalId = globalIdCacheByArtifactReference.get(artifactReference);
            if (globalId != null) {
                ParsedSchema<S> schema = schemasCache.get(globalId);
                if (schema != null) {
                    return result.schema(schema.getParsedSchema())
                            .rawSchema(schema.getRawSchema())
                            .globalId(globalId)
                            .groupId(artifactReference.getGroupId())
                            .artifactId(artifactReference.getArtifactId())
                            .version(artifactReference.getVersion())
                            .build();
                }
            }
        }


        if (autoCreateArtifact && parsedSchema.isPresent()) {

            byte[] rawSchema = parsedSchema.get().getRawSchema();
            String rawSchemaString = IoUtil.toString(rawSchema);
            S schema = parsedSchema.get().getParsedSchema();
            result.rawSchema(rawSchema);
            result.schema(schema);

            globalIdCacheByContent.computeIfAbsent(rawSchemaString, key -> {
                ArtifactMetaData artifactMetadata = client.createArtifact(artifactReference.getGroupId(), artifactReference.getArtifactId(), artifactReference.getVersion(), schemaParser.artifactType(), this.autoCreateBehavior, false, IoUtil.toStream(rawSchema));
                loadFromArtifactMetaData(artifactMetadata, result);

                Long newGlobalId = artifactMetadata.getGlobalId();
                schemasCache.put(newGlobalId, parsedSchema.get());
                globalIdCacheByArtifactReference.put(artifactReference, newGlobalId);
                return newGlobalId;
            });

            return result.build();

        } else {

            ArtifactReference latestArtifactReference = ArtifactReference.builder()
                    .groupId(artifactReference.getGroupId())
                    .artifactId(artifactReference.getArtifactId())
                    .build();

            globalIdCacheByArtifactReference.computeIfAbsent(latestArtifactReference, k -> {
                //TODO if getLatestArtifact returns the artifact version and globalid in the headers we can reduce this to only one http call
                ArtifactMetaData artifactMetadata = client.getArtifactMetaData(artifactReference.getGroupId(), artifactReference.getArtifactId());
                InputStream rawSchemaStream = client.getLatestArtifact(artifactReference.getGroupId(), artifactReference.getArtifactId());

                byte[] rawSchema = IoUtil.toBytes(rawSchemaStream);
                S schema = schemaParser.parseSchema(rawSchema);
                result.rawSchema(rawSchema);
                result.schema(schema);
                loadFromArtifactMetaData(artifactMetadata, result);

                Long latestGlobalId = artifactMetadata.getGlobalId();
                schemasCache.put(latestGlobalId, new ParsedSchema<S>().setRawSchema(rawSchema).setParsedSchema(schema));
                globalIdCacheByContent.put(IoUtil.toString(rawSchema), latestGlobalId);
                return latestGlobalId;
            });

            return result.build();
        }

    }

    private void loadFromArtifactMetaData(ArtifactMetaData artifactMetadata, SchemaLookupResult.SchemaLookupResultBuilder<S> resultBuilder) {
        resultBuilder.globalId(artifactMetadata.getGlobalId());
        resultBuilder.groupId(artifactMetadata.getGroupId());
        resultBuilder.artifactId(artifactMetadata.getId());
        resultBuilder.version(String.valueOf(artifactMetadata.getVersion()));
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchemaByGlobalId(long)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByGlobalId(long globalId) {

        //TODO use caches

        ParsedSchema<S> parsedSchema = schemasCache.computeIfAbsent(globalId, k -> {
            //TODO getContentByGlobalId have to return some minumum metadata (groupId, artifactId and version)
            //TODO or at least add some methd to the api to return the version metadata by globalId
//            ArtifactMetaData artifactMetadata = client.getArtifactMetaData("TODO", artifactId);
            InputStream rawSchema = client.getContentByGlobalId(globalId);

            byte[] schema = IoUtil.toBytes(rawSchema);
            S parsed = schemaParser.parseSchema(schema);
            return new ParsedSchema<S>().setParsedSchema(parsed).setRawSchema(schema);
        });

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();
        return result
                //FIXME it's impossible to retrive this info with only the globalId
//                .groupId(null)
//                .artifactId(null)
//                .version(0)
                .globalId(globalId)
                .rawSchema(parsedSchema.getRawSchema())
                .schema(parsedSchema.getParsedSchema())
                .build();
    }

    /**
     * @see io.apicurio.registry.serde.SchemaResolver#resolveSchemaByCoordinates(java.lang.String, java.lang.String, int)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByCoordinates(String groupId, String artifactId, String version) {

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

        globalIdCacheByArtifactReference.computeIfAbsent(ArtifactReference.builder().groupId(groupId).artifactId(artifactId).version(version).build(),
                artifactReference -> {

                    //TODO if getArtifactVersion returns the artifact version and globalid in the headers we can reduce this to only one http call
                    VersionMetaData artifactMetadata = client.getArtifactVersionMetaData(groupId, artifactId, version);
                    InputStream rawSchema = client.getArtifactVersion(groupId, artifactId, version);

                    byte[] schema = IoUtil.toBytes(rawSchema);
                    S parsed = schemaParser.parseSchema(schema);

                    result
                        .rawSchema(schema)
                        .schema(parsed)
                        .groupId(artifactMetadata.getGroupId())
                        .artifactId(artifactMetadata.getId())
                        .version(String.valueOf(artifactMetadata.getVersion()))
                        .globalId(artifactMetadata.getGlobalId());

                    schemasCache.put(artifactMetadata.getGlobalId(), new ParsedSchema<S>().setRawSchema(schema).setParsedSchema(parsed));
                    globalIdCacheByContent.put(IoUtil.toString(schema), artifactMetadata.getGlobalId());
                    return artifactMetadata.getGlobalId();
                });

        return result.build();
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

//  private RegistryClient configureClientWithAuthentication(Map<String, ?> configs, String registryUrl, String authServerUrl) {
//
//      final String realm = (String) configs.get(SerdeConfigKeys.AUTH_REALM);
//
//      if (realm == null) {
//          throw new IllegalArgumentException("Missing registry auth realm, set " + SerdeConfigKeys.AUTH_REALM);
//      }
//      final String clientId = (String) configs.get(SerdeConfigKeys.AUTH_CLIENT_ID);
//
//      if (clientId == null) {
//          throw new IllegalArgumentException("Missing registry auth clientId, set " + SerdeConfigKeys.AUTH_CLIENT_ID);
//      }
//      final String clientSecret = (String) configs.get(SerdeConfigKeys.AUTH_CLIENT_SECRET);
//
//      if (clientSecret == null) {
//          throw new IllegalArgumentException("Missing registry auth secret, set " + SerdeConfigKeys.AUTH_CLIENT_SECRET);
//      }
//
//      Auth auth = new KeycloakAuth(authServerUrl, realm, clientId, clientSecret);
//
//      return RegistryClientFactory.create(registryUrl, Collections.emptyMap(), auth);
//  }


}
