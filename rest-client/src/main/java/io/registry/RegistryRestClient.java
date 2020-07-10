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
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.registry.service.ArtifactsService;
import io.registry.service.IdsService;
import io.registry.service.RulesService;
import io.registry.service.SearchService;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RegistryRestClient implements RegistryRestService {

    private static final Logger log = Logger.getLogger(RegistryRestClient.class.getName());

    private Retrofit retrofit;
    private ArtifactsService artifactsService;
    private RulesService rulesService;
    private SearchService searchService;
    private IdsService idsService;

    private RegistryRestClient(String baseUrl) {

        retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .build();

        initServices(retrofit);
    }

    private RegistryRestClient(String baseUrl, OkHttpClient okHttpClient) {

        retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(baseUrl)
                .build();

        initServices(retrofit);
    }

    private void initServices(Retrofit retrofit) {
        artifactsService = retrofit.create(ArtifactsService.class);
        rulesService = retrofit.create(RulesService.class);
        idsService = retrofit.create(IdsService.class);
        searchService = retrofit.create(SearchService.class);
    }

    @Override
    public List<String> listArtifacts() {
        return artifactsService.listArtifacts();
    }

    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType artifactType, String artifactId, IfExistsType ifExistsType, InputStream data) {
        return artifactsService.createArtifact(artifactType, artifactId, ifExistsType, data);
    }

    @Override
    public Response getLatestArtifact(String artifactId) {
        return artifactsService.getLatestArtifact(artifactId);
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
        return artifactsService.getArtifactMetaData(artifactId);
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        artifactsService.updateArtifactMetaData(artifactId, data);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId,
                                                         InputStream data) {
        return artifactsService.getArtifactMetaDataByContent(artifactId, data);
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {
        return artifactsService.listArtifactVersions(artifactId);
    }

    @Override
    public CompletionStage<VersionMetaData> createArtifactVersion(String artifactId,
                                                                  ArtifactType xRegistryArtifactType, InputStream data) {
        return artifactsService.createArtifactVersion(artifactId, xRegistryArtifactType, data);
    }

    @Override
    public Response getArtifactVersion(Integer version,
                                       String artifactId) {
        return artifactsService.getArtifactVersion(version, artifactId);
    }

    @Override
    public void updateArtifactVersionState(Integer version, String artifactId, UpdateState data) {
        artifactsService.updateArtifactVersionState(version, artifactId, data);
    }

    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        return artifactsService.getArtifactVersionMetaData(version, artifactId);
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
        return artifactsService.listArtifactRules(artifactId);
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
        return artifactsService.getArtifactRuleConfig(rule, artifactId);
    }

    @Override
    public Rule updateArtifactRuleConfig(RuleType rule,
                                         String artifactId, Rule data) {
        return artifactsService.updateArtifactRuleConfig(rule, artifactId, data);
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
        return idsService.getArtifactByGlobalId(globalId);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {
        return idsService.getArtifactMetaDataByGlobalId(globalId);
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, Integer offset, Integer limit, SearchOver over, SortOrder order) {
        return searchService.searchArtifacts(search, offset, limit, over, order);
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {
        return searchService.searchVersions(artifactId, offset, limit);
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return rulesService.getGlobalRuleConfig(rule);
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        return rulesService.updateGlobalRuleConfig(rule, data);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        rulesService.deleteGlobalRule(rule);
    }

    @Override
    public List<RuleType> listGlobalRules() {
        return rulesService.listGlobalRules();
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
}
