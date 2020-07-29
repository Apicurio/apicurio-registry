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

package io.registry.client;

import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.service.RegistryService;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.IoUtil;
import io.registry.client.callback.ResultCallback;
import io.registry.client.service.ArtifactsService;
import io.registry.client.service.IdsService;
import io.registry.client.service.RulesService;
import io.registry.client.service.SearchService;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.enterprise.inject.Vetoed;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
@Vetoed // not a bean
@Path("__dummy_hack")
public class RegistryRestService implements RegistryService {

    private static final Logger log = Logger.getLogger(RegistryRestService.class.getName());

    private final Retrofit retrofit;
    private ArtifactsService artifactsService;
    private RulesService rulesService;
    private SearchService searchService;
    private IdsService idsService;

    public RegistryRestService() {
        // hack + client side only
        retrofit = null;
    }

    protected RegistryRestService(String baseUrl) {

        retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        initServices(retrofit);
    }

    protected RegistryRestService(String baseUrl, OkHttpClient okHttpClient) {

        retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
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

        final ResultCallback<List<String>> artifacts = new ResultCallback<>();

        artifactsService.listArtifacts()
                .enqueue(artifacts);

        return artifacts.getResult();
    }

    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType artifactType, String artifactId, IfExistsType ifExistsType, InputStream data) {

        final ResultCallback<ArtifactMetaData> result = new ResultCallback<>();

        artifactsService.createArtifact(artifactType, artifactId, ifExistsType, RequestBody.create(null, IoUtil.toBytes(data)))
                .enqueue(result);

        return CompletableFuture.completedFuture(result.getResult());
    }

    @Override
    public Response getLatestArtifact(String artifactId) {

        final ResultCallback<ResponseBody> resultCallback = new ResultCallback<>();

        artifactsService.getLatestArtifact(artifactId)
                .enqueue(resultCallback);

        ResponseBody result = resultCallback.getResult();

        return parseResponseBody(result);
    }

    @Override
    public CompletionStage<ArtifactMetaData> updateArtifact(String artifactId,
                                                            ArtifactType xRegistryArtifactType, InputStream data) {

        final ResultCallback<ArtifactMetaData> result = new ResultCallback<>();

        artifactsService.updateArtifact(artifactId, xRegistryArtifactType, RequestBody.create(null, IoUtil.toBytes(data)))
                .enqueue(result);

        return CompletableFuture.completedFuture(result.getResult());
    }

    @Override
    public void deleteArtifact(String artifactId) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.deleteArtifact(artifactId)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public void updateArtifactState(String artifactId, UpdateState data) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.updateArtifactState(artifactId, data)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {

        final ResultCallback<ArtifactMetaData> resultCallback = new ResultCallback<>();

        artifactsService.getArtifactMetaData(artifactId)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.updateArtifactMetaData(artifactId, data)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId,
                                                         InputStream data) {

        final ResultCallback<ArtifactMetaData> resultCallback = new ResultCallback<>();

        artifactsService.getArtifactMetaDataByContent(artifactId, RequestBody.create(null, IoUtil.toBytes(data)))
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {

        final ResultCallback<List<Long>> resultCallback = new ResultCallback<>();

        artifactsService.listArtifactVersions(artifactId)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public CompletionStage<VersionMetaData> createArtifactVersion(String artifactId,
                                                                  ArtifactType xRegistryArtifactType, InputStream data) {

        final ResultCallback<VersionMetaData> result = new ResultCallback<>();

        artifactsService.createArtifactVersion(artifactId, xRegistryArtifactType, RequestBody.create(null, IoUtil.toBytes(data)))
                .enqueue(result);

        return CompletableFuture.completedFuture(result.getResult());
    }

    @Override
    public Response getArtifactVersion(Integer version,
                                       String artifactId) {

        final ResultCallback<ResponseBody> resultCallback = new ResultCallback<>();

        artifactsService.getArtifactVersion(version, artifactId)
                .enqueue(resultCallback);

        final ResponseBody result = resultCallback.getResult();

        return parseResponseBody(result);
    }

    @Override
    public void updateArtifactVersionState(Integer version, String artifactId, UpdateState data) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.updateArtifactVersionState(version, artifactId, data)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {

        final ResultCallback<VersionMetaData> resultCallback = new ResultCallback<>();

        artifactsService.getArtifactVersionMetaData(version, artifactId)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.updateArtifactVersionMetaData(version, artifactId, data)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public void deleteArtifactVersionMetaData(Integer version, String artifactId) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.deleteArtifactVersionMetaData(version, artifactId)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public List<RuleType> listArtifactRules(String artifactId) {

        final ResultCallback<List<RuleType>> resultCallback = new ResultCallback<>();

        artifactsService.listArtifactRules(artifactId)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public void createArtifactRule(String artifactId, Rule data) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.createArtifactRule(artifactId, data)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public void deleteArtifactRules(String artifactId) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.deleteArtifactRules(artifactId)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public Rule getArtifactRuleConfig(RuleType rule,
                                      String artifactId) {

        final ResultCallback<Rule> resultCallback = new ResultCallback<>();

        artifactsService.getArtifactRuleConfig(rule, artifactId)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public Rule updateArtifactRuleConfig(RuleType rule,
                                         String artifactId, Rule data) {

        final ResultCallback<Rule> resultCallback = new ResultCallback<>();

        artifactsService.updateArtifactRuleConfig(rule, artifactId, data)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public void deleteArtifactRule(RuleType rule, String artifactId) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.deleteArtifactRule(rule, artifactId)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public void testUpdateArtifact(String artifactId,
                                   ArtifactType xRegistryArtifactType, InputStream data) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        artifactsService.testUpdateArtifact(artifactId, xRegistryArtifactType, RequestBody.create(null, IoUtil.toBytes(data)))
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public Response getArtifactByGlobalId(long globalId) {

        final ResultCallback<ResponseBody> resultCallback = new ResultCallback<>();

        idsService.getArtifactByGlobalId(globalId)
                .enqueue(resultCallback);

        final ResponseBody result = resultCallback.getResult();

        return parseResponseBody(result);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {

        final ResultCallback<ArtifactMetaData> resultCallback = new ResultCallback<>();

        idsService.getArtifactMetaDataByGlobalId(globalId)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, Integer offset, Integer limit, SearchOver over, SortOrder order) {

        final ResultCallback<ArtifactSearchResults> resultCallback = new ResultCallback<>();

        searchService.searchArtifacts(search, offset, limit, over, order)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {

        final ResultCallback<VersionSearchResults> resultCallback = new ResultCallback<>();

        searchService.searchVersions(artifactId, offset, limit)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {

        final ResultCallback<Rule> resultCallback = new ResultCallback<>();

        rulesService.getGlobalRuleConfig(rule)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {

        final ResultCallback<Rule> resultCallback = new ResultCallback<>();

        rulesService.updateGlobalRuleConfig(rule, data)
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        rulesService.deleteGlobalRule(rule)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public List<RuleType> listGlobalRules() {

        final ResultCallback<List<RuleType>> resultCallback = new ResultCallback<>();

        rulesService.listGlobalRules()
                .enqueue(resultCallback);

        return resultCallback.getResult();
    }

    @Override
    public void createGlobalRule(Rule data) {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        rulesService.createGlobalRule(data)
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public void deleteAllGlobalRules() {

        final ResultCallback<Void> resultCallback = new ResultCallback<>();

        rulesService.deleteAllGlobalRules()
                .enqueue(resultCallback);

        resultCallback.getResult();
    }

    @Override
    public void close() {
    }

    @Override
    public void reset() {

    }

    private Response parseResponseBody(ResponseBody result) {

        return Response.ok(result.byteStream(), MediaType.valueOf(result.contentType().toString())).build();
    }
}
