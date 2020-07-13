/*
 * Copyright 2020 Red Hat
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

package io.registry;

import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.service.RegistryService;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.registry.service.ArtifactsService;
import io.registry.service.IdsService;
import io.registry.service.RulesService;
import io.registry.service.SearchService;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RegistryRestService implements RegistryService {

    private static final Logger log = Logger.getLogger(RegistryRestService.class.getName());

    private final Retrofit retrofit;
    private ArtifactsService artifactsService;
    private RulesService rulesService;
    private SearchService searchService;
    private IdsService idsService;

    protected RegistryRestService(String baseUrl) {

        retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        initServices(retrofit);
    }

    protected RegistryRestService(String baseUrl, OkHttpClient okHttpClient) {

        retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(baseUrl)
                .build();

        initServices(retrofit);
    }

    public static RegistryService create(String baseUrl) {
        return new RegistryRestService(baseUrl);
    }

    public static RegistryService create(String baseUrl, OkHttpClient okHttpClient) {
        return new RegistryRestService(baseUrl, okHttpClient);
    }

    private void initServices(Retrofit retrofit) {
        artifactsService = retrofit.create(ArtifactsService.class);
        rulesService = retrofit.create(RulesService.class);
        idsService = retrofit.create(IdsService.class);
        searchService = retrofit.create(SearchService.class);
    }

    @Override
    public List<String> listArtifacts() {
        try {
            return artifactsService.listArtifacts().execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType artifactType, String artifactId, IfExistsType ifExistsType, InputStream data) {
        return artifactsService.createArtifact(artifactType, artifactId, ifExistsType, data);
    }

    @Override
    public Response getLatestArtifact(String artifactId) {
        try {
            return artifactsService.getLatestArtifact(artifactId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public CompletionStage<ArtifactMetaData> updateArtifact(String artifactId,
                                                            ArtifactType xRegistryArtifactType, InputStream data) {
        return artifactsService.updateArtifact(artifactId, xRegistryArtifactType, data);
    }

    @Override
    public void deleteArtifact(String artifactId) {
        artifactsService.deleteArtifact(artifactId);
    }

    @Override
    public void updateArtifactState(String artifactId, UpdateState data) {
        artifactsService.updateArtifactState(artifactId, data);
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        try {
            return artifactsService.getArtifactMetaData(artifactId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        artifactsService.updateArtifactMetaData(artifactId, data);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId,
                                                         InputStream data) {
        try {
            return artifactsService.getArtifactMetaDataByContent(artifactId, data).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {
        try {
            return artifactsService.listArtifactVersions(artifactId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public CompletionStage<VersionMetaData> createArtifactVersion(String artifactId,
                                                                  ArtifactType xRegistryArtifactType, InputStream data) {
        return artifactsService.createArtifactVersion(artifactId, xRegistryArtifactType, data);
    }

    @Override
    public Response getArtifactVersion(Integer version,
                                       String artifactId) {
        try {
            return artifactsService.getArtifactVersion(version, artifactId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void updateArtifactVersionState(Integer version, String artifactId, UpdateState data) {
        artifactsService.updateArtifactVersionState(version, artifactId, data);
    }

    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        try {
            return artifactsService.getArtifactVersionMetaData(version, artifactId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data) {
        artifactsService.updateArtifactVersionMetaData(version, artifactId, data);
    }

    @Override
    public void deleteArtifactVersionMetaData(Integer version, String artifactId) {
        artifactsService.deleteArtifactVersionMetaData(version, artifactId);
    }

    @Override
    public List<RuleType> listArtifactRules(String artifactId) {
        try {
            return artifactsService.listArtifactRules(artifactId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void createArtifactRule(String artifactId, Rule data) {
        artifactsService.createArtifactRule(artifactId, data);
    }

    @Override
    public void deleteArtifactRules(String artifactId) {
        artifactsService.deleteArtifactRules(artifactId);
    }

    @Override
    public Rule getArtifactRuleConfig(RuleType rule,
                                      String artifactId) {
        try {
            return artifactsService.getArtifactRuleConfig(rule, artifactId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Rule updateArtifactRuleConfig(RuleType rule,
                                         String artifactId, Rule data) {
        try {
            return artifactsService.updateArtifactRuleConfig(rule, artifactId, data).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void deleteArtifactRule(RuleType rule, String artifactId) {
        artifactsService.deleteArtifactRule(rule, artifactId);
    }

    @Override
    public void testUpdateArtifact(String artifactId,
                                   ArtifactType xRegistryArtifactType, InputStream data) {
        artifactsService.testUpdateArtifact(artifactId, xRegistryArtifactType, data);
    }

    @Override
    public Response getArtifactByGlobalId(long globalId) {
        try {
            return idsService.getArtifactByGlobalId(globalId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {
        try {
            return idsService.getArtifactMetaDataByGlobalId(globalId).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, Integer offset, Integer limit, SearchOver over, SortOrder order) {
        try {
            return searchService.searchArtifacts(search, offset, limit, over, order).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {
        try {
            return searchService.searchVersions(artifactId, offset, limit).execute().body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        try {
            return rulesService.getGlobalRuleConfig(rule)
                    .execute()
                    .body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        try {
            return rulesService.updateGlobalRuleConfig(rule, data)
                    .execute()
                    .body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        rulesService.deleteGlobalRule(rule);
    }

    @Override
    public List<RuleType> listGlobalRules() {
        try {
            return rulesService.listGlobalRules()
                    .execute()
                    .body();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void createGlobalRule(Rule data) {
        rulesService.createGlobalRule(data);
    }

    @Override
    public void deleteAllGlobalRules() {
        rulesService.deleteAllGlobalRules();
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public void reset() {

    }
}
