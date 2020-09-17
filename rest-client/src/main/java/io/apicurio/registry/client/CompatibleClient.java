/*
 * Copyright 2020 Red Hat
 * Copyright 2020 IBM
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

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.beans.EditableMetaData;
import io.apicurio.registry.rest.beans.IfExistsType;
import io.apicurio.registry.rest.beans.Rule;
import io.apicurio.registry.rest.beans.SearchOver;
import io.apicurio.registry.rest.beans.SortOrder;
import io.apicurio.registry.rest.beans.UpdateState;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.rest.beans.VersionSearchResults;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import javax.enterprise.inject.Vetoed;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
@SuppressWarnings("deprecation")
@Vetoed // not a bean
@Path("__dummy_hack_apicurio")
public class CompatibleClient implements RegistryService {

    private final RegistryRestClient delegate;

    public CompatibleClient() {
        // hack + client side only
        delegate = null;
    }

    private CompatibleClient(String baseUrl) {
        this.delegate = RegistryRestClientFactory.create(baseUrl);
    }

    private CompatibleClient(String baseUrl, Map<String, Object> configs) {
        this.delegate = RegistryRestClientFactory.create(baseUrl, configs);
    }

    public static RegistryService createCompatible(String baseUrl) {
        return new CompatibleClient(baseUrl);
    }

    public static RegistryService createCompatible(String baseUrl, Map<String, Object> configs) {
        return new CompatibleClient(baseUrl, configs);
    }

    @Override
    public List<String> listArtifacts() {
        return delegate.listArtifacts();
    }

    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType xRegistryArtifactType, String xRegistryArtifactId, IfExistsType ifExists, InputStream data) {
        return CompletableFuture.completedFuture(delegate.createArtifact(xRegistryArtifactId, xRegistryArtifactType, ifExists, data));
    }

    @Override
    public Response getLatestArtifact(String artifactId) {
        return parseResponse(delegate.getLatestArtifact(artifactId));
    }

    @Override
    public CompletionStage<ArtifactMetaData> updateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        return CompletableFuture.completedFuture(delegate.updateArtifact(artifactId, xRegistryArtifactType, data));
    }

    @Override
    public void deleteArtifact(String artifactId) {
        delegate.deleteArtifact(artifactId);
    }

    @Override
    public void updateArtifactState(String artifactId, UpdateState data) {
        delegate.updateArtifactState(artifactId, data);
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        return delegate.getArtifactMetaData(artifactId);
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        delegate.updateArtifactMetaData(artifactId, data);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId, InputStream data) {
        return delegate.getArtifactMetaDataByContent(artifactId, data);
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {
        return delegate.listArtifactVersions(artifactId);
    }

    @Override
    public CompletionStage<VersionMetaData> createArtifactVersion(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        return CompletableFuture.completedFuture(delegate.createArtifactVersion(artifactId, xRegistryArtifactType, data));
    }

    @Override
    public Response getArtifactVersion(Integer version, String artifactId) {
        return parseResponse(delegate.getArtifactVersion(artifactId, version));
    }

    @Override
    public void updateArtifactVersionState(Integer version, String artifactId, UpdateState data) {
        delegate.updateArtifactVersionState(artifactId, version, data);
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        return delegate.getArtifactVersionMetaData(artifactId, version);
    }

    @Override
    public void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data) {
        delegate.updateArtifactVersionMetaData(artifactId, version, data);
    }

    @Override
    public void deleteArtifactVersionMetaData(Integer version, String artifactId) {
        delegate.deleteArtifactVersionMetaData(artifactId, version);
    }

    @Override
    public List<RuleType> listArtifactRules(String artifactId) {
        return delegate.listArtifactRules(artifactId);
    }

    @Override
    public void createArtifactRule(String artifactId, Rule data) {
        delegate.createArtifactRule(artifactId, data);
    }

    @Override
    public void deleteArtifactRules(String artifactId) {
        delegate.deleteArtifactRules(artifactId);
    }

    @Override
    public Rule getArtifactRuleConfig(RuleType rule, String artifactId) {
        return delegate.getArtifactRuleConfig(artifactId, rule);
    }

    @Override
    public Rule updateArtifactRuleConfig(RuleType rule, String artifactId, Rule data) {
        return delegate.updateArtifactRuleConfig(artifactId, rule, data);
    }

    @Override
    public void deleteArtifactRule(RuleType rule, String artifactId) {
        delegate.deleteArtifactRule(artifactId, rule);
    }

    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        delegate.testUpdateArtifact(artifactId, xRegistryArtifactType, data);
    }

    @Override
    public Response getArtifactByGlobalId(long globalId) {
        return parseResponse(delegate.getArtifactByGlobalId(globalId));
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {
        return delegate.getArtifactMetaDataByGlobalId(globalId);
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType rule) {
        return delegate.getGlobalRuleConfig(rule);
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType rule, Rule data) {
        return delegate.updateGlobalRuleConfig(rule, data);
    }

    @Override
    public void deleteGlobalRule(RuleType rule) {
        delegate.deleteGlobalRule(rule);
    }

    @Override
    public List<RuleType> listGlobalRules() {
        return delegate.listGlobalRules();
    }

    @Override
    public void createGlobalRule(Rule data) {
        delegate.createGlobalRule(data);
    }

    @Override
    public void deleteAllGlobalRules() {
        delegate.deleteAllGlobalRules();
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, Integer offset, Integer limit, SearchOver over, SortOrder order) {
        return delegate.searchArtifacts(search, over, order, offset, limit);
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {
        return delegate.searchVersions(artifactId, offset, limit);
    }

    @Override
    public void close() throws Exception {

    }

    @Override
    public void reset() {

    }

    private Response parseResponse(InputStream resultStream) {

        return Response.ok(resultStream).build();
    }
}
