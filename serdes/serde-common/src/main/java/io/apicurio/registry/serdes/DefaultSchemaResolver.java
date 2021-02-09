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

package io.apicurio.registry.serdes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.common.header.Headers;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.serdes.strategy.ArtifactIdStrategy;
import io.apicurio.registry.serdes.strategy.TopicIdStrategy;
import io.apicurio.registry.serdes.utils.Utils;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Fabian Martinez
 */
public class DefaultSchemaResolver<S, T> implements SchemaResolver<S, T>{

    //TODO improve cache and add refresh period
    private final Map<Long, S> schemasCache = new ConcurrentHashMap<>();
    private Map<String, Long> schemasCacheByContent = new ConcurrentHashMap<>();

    private SchemaMapper<S, T> schemaMapper;
    private RegistryClient client;
    private boolean isKey;
    private ArtifactIdStrategy<S> artifactIdStrategy;

    private boolean autoCreateArtifact;
    private IfExists autoCreateBehavior;
//    private boolean useLatestArtifact;

    /**
     * @see io.apicurio.registry.serdes.SchemaResolver#configure(java.util.Map, boolean)
     */
    @Override
    public void configure(Map<String, ?> configs, boolean isKey, SchemaMapper<S, T> schemaMapper) {
        this.schemaMapper = schemaMapper;
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
            if (this.artifactIdStrategy == null) {
                this.setArtifactIdStrategy(new TopicIdStrategy<>());
            }
        } else {
            Utils.instantiate(ArtifactIdStrategy.class, ais, this::setArtifactIdStrategy);
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
     * @param artifactIdStrategy the artifactIdStrategy to set
     */
    @Override
    public void setArtifactIdStrategy(ArtifactIdStrategy<S> artifactIdStrategy) {
        this.artifactIdStrategy = artifactIdStrategy;
    }

    /**
     * @param isKey the isKey to set
     */
    public void setIsKey(boolean isKey) {
        this.isKey = isKey;
    }

    /**
     * @see io.apicurio.registry.serdes.SchemaResolver#resolveSchema(java.lang.String, org.apache.kafka.common.header.Headers, java.lang.Object)
     */
    @Override
    public SchemaLookupResult<S> resolveSchema(String topic, Headers headers, T data) {

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();

        S schema = schemaMapper.schemaFromData(data);
        result.parsedSchema(schema);

        String artifactId = artifactIdStrategy.artifactId(topic, isKey, schema);
        result.artifactId(artifactId);

        if (autoCreateArtifact) {

            //TODO use caches

            byte[] rawSchema = IoUtil.toBytes(schemaMapper.toRawSchema(schema));
            result.rawSchema(rawSchema);

            ArtifactMetaData artifactMetadata = client.createArtifact("TODO", schemaMapper.artifactType(), artifactId, null, this.autoCreateBehavior, false, IoUtil.toStream(rawSchema));

            long globalId = artifactMetadata.getGlobalId();

            schemasCache.put(globalId, schema);
            schemasCacheByContent.put(IoUtil.toString(rawSchema), globalId);

            fromArtifactMetaData(artifactMetadata, result);

        } else {

            //TODO use caches

            //TODO if getLatestArtifact returns the artifact version and globalid in the headers we can reduce this to only one http call
            ArtifactMetaData artifactMetadata = client.getArtifactMetaData("TODO", artifactId);
            InputStream rawSchema = client.getLatestArtifact("TODO", artifactId);
            result.rawSchema(IoUtil.toBytes(rawSchema));

            long globalId = artifactMetadata.getGlobalId();
            schemasCache.put(globalId, schema);
            schemasCacheByContent.put(IoUtil.toString(rawSchema), globalId);

            fromArtifactMetaData(artifactMetadata, result);
        }

        return result.build();
    }

    private void fromArtifactMetaData(ArtifactMetaData artifactMetadata, SchemaLookupResult.SchemaLookupResultBuilder<S> resultBuilder) {
        resultBuilder.globalId(artifactMetadata.getGlobalId());
        resultBuilder.groupId(artifactMetadata.getGroupId());
        resultBuilder.artifactId(artifactMetadata.getId());
        resultBuilder.version(artifactMetadata.getVersion().intValue());
    }

    /**
     * @see io.apicurio.registry.serdes.SchemaResolver#resolveSchemaByGlobalId(long)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByGlobalId(long globalId) {

        //TODO use caches

        //TODO getContentByGlobalId have to return some minumum metadata (groupId, artifactId and version)
        //ArtifactMetaData artifactMetadata = client.getArtifactMetaData("TODO", artifactId);
        InputStream rawSchema = client.getContentByGlobalId(globalId);

        byte[] schema = IoUtil.toBytes(rawSchema);
        S parsed = schemaMapper.parseSchema(new ByteArrayInputStream(schema));

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();
        return result
//                .groupId(null)
//                .artifactId(null)
//                .version(0)
                .globalId(globalId)
                .rawSchema(schema)
                .parsedSchema(parsed)
                .build();
    }

    /**
     * @see io.apicurio.registry.serdes.SchemaResolver#resolveSchemaByArtifactId(java.lang.String, java.lang.String, int)
     */
    @Override
    public SchemaLookupResult<S> resolveSchemaByArtifactId(String groupId, String artifactId, int version) {

        //TODO use caches

        //TODO if getArtifactVersion returns the artifact version and globalid in the headers we can reduce this to only one http call
        VersionMetaData artifactMetadata = client.getArtifactVersionMetaData(groupId, artifactId, version);
        InputStream rawSchema = client.getArtifactVersion(groupId, artifactId, version);

        byte[] schema = IoUtil.toBytes(rawSchema);
        S parsed = schemaMapper.parseSchema(new ByteArrayInputStream(schema));

        SchemaLookupResult.SchemaLookupResultBuilder<S> result = SchemaLookupResult.builder();
        return result
                .groupId(artifactMetadata.getGroupId())
                .artifactId(artifactMetadata.getId())
                .version(artifactMetadata.getVersion().intValue())
                .globalId(artifactMetadata.getGlobalId())
                .rawSchema(schema)
                .parsedSchema(parsed)
                .build();
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
