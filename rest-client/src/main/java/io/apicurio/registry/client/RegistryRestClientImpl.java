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

package io.apicurio.registry.client;

import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.client.request.HeadersInterceptor;
import io.apicurio.registry.client.request.RequestHandler;
import io.apicurio.registry.client.service.ArtifactsService;
import io.apicurio.registry.client.service.IdsService;
import io.apicurio.registry.client.service.RulesService;
import io.apicurio.registry.client.service.SearchService;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class RegistryRestClientImpl implements RegistryRestClient {

    private final Retrofit retrofit;
    private final RequestHandler requestHandler;

    private ArtifactsService artifactsService;
    private RulesService rulesService;
    private SearchService searchService;
    private IdsService idsService;

    RegistryRestClientImpl(String baseUrl) {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        this.retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        this.requestHandler = new RequestHandler();

        initServices(retrofit);
    }

    RegistryRestClientImpl(String baseUrl, OkHttpClient okHttpClient) {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        this.retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
                .baseUrl(baseUrl)
                .build();

        this.requestHandler = new RequestHandler();

        initServices(retrofit);
    }

    RegistryRestClientImpl(String baseUrl, Map<String, String> headers) {
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }

        final OkHttpClient okHttpClient = createWithHeaders(headers);

        this.retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
                .baseUrl(baseUrl)
                .build();

        this.requestHandler = new RequestHandler();

        initServices(retrofit);
    }

    private static OkHttpClient createWithHeaders(Map<String, String> headers) {

        final Interceptor headersInterceptor = new HeadersInterceptor(headers);

        return new OkHttpClient.Builder()
                .addInterceptor(headersInterceptor)
                .build();
    }

    private void initServices(Retrofit retrofit) {
        artifactsService = retrofit.create(ArtifactsService.class);
        rulesService = retrofit.create(RulesService.class);
        idsService = retrofit.create(IdsService.class);
        searchService = retrofit.create(SearchService.class);
    }

    @Override
    public List<String> listArtifacts() {

        return requestHandler.execute(artifactsService.listArtifacts());
    }

    @Override
    public ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, IfExistsType ifExistsType, InputStream data) {

        return requestHandler.execute(artifactsService.createArtifact(artifactType, artifactId, ifExistsType, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public InputStream getLatestArtifact(String artifactId) {

        return requestHandler.execute(artifactsService.getLatestArtifact(artifactId)).byteStream();
    }

    @Override
    public ArtifactMetaData updateArtifact(String artifactId, ArtifactType artifactType, InputStream data) {

        return requestHandler.execute(artifactsService.updateArtifact(artifactId, artifactType, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public void deleteArtifact(String artifactId) {

        requestHandler.execute(artifactsService.deleteArtifact(artifactId));
    }

    @Override
    public void updateArtifactState(String artifactId, UpdateState data) {

        requestHandler.execute(artifactsService.updateArtifactState(artifactId, data));
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {

        return requestHandler.execute(artifactsService.getArtifactMetaData(artifactId));
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {

        requestHandler.execute(artifactsService.updateArtifactMetaData(artifactId, data));
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId, InputStream data) {

        return requestHandler.execute(artifactsService.getArtifactMetaDataByContent(artifactId, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {

        return requestHandler.execute(artifactsService.listArtifactVersions(artifactId));
    }

    @Override
    public VersionMetaData createArtifactVersion(String artifactId, ArtifactType artifactType, InputStream data) {

        return requestHandler.execute(artifactsService.createArtifactVersion(artifactId, artifactType, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public InputStream getArtifactVersion(String artifactId, Integer version) {

        return requestHandler.execute(artifactsService.getArtifactVersion(version, artifactId)).byteStream();
    }

    @Override
    public void updateArtifactVersionState(String artifactId, Integer version, UpdateState data) {

        requestHandler.execute(artifactsService.updateArtifactVersionState(version, artifactId, data));
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(String artifactId, Integer version) {

        return requestHandler.execute(artifactsService.getArtifactVersionMetaData(version, artifactId));
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, Integer version, EditableMetaData data) {

        requestHandler.execute(artifactsService.updateArtifactVersionMetaData(version, artifactId, data));
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, Integer version) {

        requestHandler.execute(artifactsService.deleteArtifactVersionMetaData(version, artifactId));
    }

    @Override
    public List<RuleType> listArtifactRules(String artifactId) {

        return requestHandler.execute(artifactsService.listArtifactRules(artifactId));
    }

    @Override
    public void createArtifactRule(String artifactId, Rule data) {

        requestHandler.execute(artifactsService.createArtifactRule(artifactId, data));
    }

    @Override
    public void deleteArtifactRules(String artifactId) {

        requestHandler.execute(artifactsService.deleteArtifactRules(artifactId));
    }

    @Override
    public Rule getArtifactRuleConfig(String artifactId, RuleType rule) {

        return requestHandler.execute(artifactsService.getArtifactRuleConfig(rule, artifactId));
    }

    @Override
    public Rule updateArtifactRuleConfig(String artifactId, RuleType rule, Rule data) {

        return requestHandler.execute(artifactsService.updateArtifactRuleConfig(rule, artifactId, data));
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType rule) {

        requestHandler.execute(artifactsService.deleteArtifactRule(rule, artifactId));
    }

    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType artifactType, InputStream data) {

        requestHandler.execute(artifactsService.testUpdateArtifact(artifactId, artifactType, RequestBody.create(MediaType.parse("*/*"), IoUtil.toBytes(data))));
    }

    @Override
    public InputStream getArtifactByGlobalId(long globalId) {

        return requestHandler.execute(idsService.getArtifactByGlobalId(globalId)).byteStream();
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {

        return requestHandler.execute(idsService.getArtifactMetaDataByGlobalId(globalId));
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, SearchOver over, SortOrder order, Integer offset, Integer limit) {

        return requestHandler.execute(searchService.searchArtifacts(search, offset, limit, over, order));
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {

        return requestHandler.execute(searchService.searchVersions(artifactId, offset, limit));
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {

        return requestHandler.execute(rulesService.getGlobalRuleConfig(rule));
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {

        return requestHandler.execute(rulesService.updateGlobalRuleConfig(rule, data));
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {

        requestHandler.execute(rulesService.deleteGlobalRule(rule));
    }

    @Override
    public List<RuleType> listGlobalRules() {

        return requestHandler.execute(rulesService.listGlobalRules());
    }

    @Override
    public void createGlobalRule(Rule data) {

        requestHandler.execute(rulesService.createGlobalRule(data));
    }

    @Override
    public void deleteAllGlobalRules() {

        requestHandler.execute(rulesService.deleteAllGlobalRules());
    }
}
