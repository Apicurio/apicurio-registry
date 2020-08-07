package io.registry.client;

import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.service.RegistryService;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CompatibleRestClient implements RegistryService {

    private final RegistryRestClient delegate;

    private CompatibleRestClient(String baseUrl) {
        this.delegate = new RegistryRestClient(baseUrl);
    }

    public static RegistryService createCompatible(String baseUrl) {
        return new CompatibleRestClient(baseUrl);
    }

    @Override
    public List<String> listArtifacts() {
        return delegate.listArtifacts();
    }

    @Override
    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType xRegistryArtifactType, String xRegistryArtifactId, IfExistsType ifExists, InputStream data) {
        return CompletableFuture.completedFuture(delegate.createArtifact(xRegistryArtifactType, xRegistryArtifactId, ifExists, data));
    }

    @Override
    public Response getLatestArtifact(String artifactId) {
        return delegate.getLatestArtifact(artifactId);
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
        return delegate.getArtifactVersion(version, artifactId);
    }

    @Override
    public void updateArtifactVersionState(Integer version, String artifactId, UpdateState data) {
        delegate.updateArtifactVersionState(version, artifactId, data);
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        return delegate.getArtifactVersionMetaData(version, artifactId);
    }

    @Override
    public void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data) {
        delegate.updateArtifactVersionMetaData(version, artifactId, data);
    }

    @Override
    public void deleteArtifactVersionMetaData(Integer version, String artifactId) {
        delegate.deleteArtifactVersionMetaData(version, artifactId);
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
        return delegate.getArtifactRuleConfig(rule, artifactId);
    }

    @Override
    public Rule updateArtifactRuleConfig(RuleType rule, String artifactId, Rule data) {
        return delegate.updateArtifactRuleConfig(rule, artifactId, data);
    }

    @Override
    public void deleteArtifactRule(RuleType rule, String artifactId) {
        delegate.deleteArtifactRule(rule, artifactId);
    }

    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data) {
        delegate.testUpdateArtifact(artifactId, xRegistryArtifactType, data);
    }

    @Override
    public Response getArtifactByGlobalId(long globalId) {
        return delegate.getArtifactByGlobalId(globalId);
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
        return delegate.searchArtifacts(search, offset, limit, over, order);
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
}
