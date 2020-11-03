package io.apicurio.registry.utils.tests;

import java.io.InputStream;
import java.util.List;
import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.client.RegistryRestClientFactory;
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

/**
 * @author famartin
 */
public class LazyRegistryRestClient implements RegistryRestClient {

    private RegistryRestClient instance;
    private String apiUrl;

    public LazyRegistryRestClient(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    private synchronized RegistryRestClient getInstance() {
        if (instance == null) {
            instance = RegistryRestClientFactory.create(apiUrl);
        }
        return instance;
    }

    @Override
    public void close() throws Exception {
        getInstance().close();
    }

    @Override
    public List<String> listArtifacts() {
        return getInstance().listArtifacts();
    }

    @Override
    public ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, IfExistsType ifExists, InputStream data) {
        return getInstance().createArtifact(artifactId, artifactType, ifExists, data);
    }

    @Override
    public InputStream getLatestArtifact(String artifactId) {
        return getInstance().getLatestArtifact(artifactId);
    }

    @Override
    public ArtifactMetaData updateArtifact(String artifactId, ArtifactType artifactType, InputStream data) {
        return getInstance().updateArtifact(artifactId, artifactType, data);
    }

    @Override
    public void deleteArtifact(String artifactId) {
        getInstance().deleteArtifact(artifactId);
    }

    @Override
    public void updateArtifactState(String artifactId, UpdateState newState) {
        getInstance().updateArtifactState(artifactId, newState);
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        return getInstance().getArtifactMetaData(artifactId);
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData metaData) {
        getInstance().updateArtifactMetaData(artifactId, metaData);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId, InputStream data) {
        return getInstance().getArtifactMetaDataByContent(artifactId, data);
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {
        return getInstance().listArtifactVersions(artifactId);
    }

    @Override
    public VersionMetaData createArtifactVersion(String artifactId, ArtifactType artifactType, InputStream data) {
        return getInstance().createArtifactVersion(artifactId, artifactType, data);
    }

    @Override
    public InputStream getArtifactVersion(String artifactId, Integer version) {
        return getInstance().getArtifactVersion(artifactId, version);
    }

    @Override
    public void updateArtifactVersionState(String artifactId, Integer version, UpdateState newState) {
        getInstance().updateArtifactVersionState(artifactId, version, newState);
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(String artifactId, Integer version) {
        return getInstance().getArtifactVersionMetaData(artifactId, version);
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, Integer version, EditableMetaData metaData) {
        getInstance().updateArtifactVersionMetaData(artifactId, version, metaData);
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, Integer version) {
        getInstance().deleteArtifactVersionMetaData(artifactId, version);
    }

    @Override
    public List<RuleType> listArtifactRules(String artifactId) {
        return getInstance().listArtifactRules(artifactId);
    }

    @Override
    public void createArtifactRule(String artifactId, Rule ruleConfig) {
        getInstance().createArtifactRule(artifactId, ruleConfig);
    }

    @Override
    public void deleteArtifactRules(String artifactId) {
        getInstance().deleteArtifactRules(artifactId);
    }

    @Override
    public Rule getArtifactRuleConfig(String artifactId, RuleType ruleType) {
        return getInstance().getArtifactRuleConfig(artifactId, ruleType);
    }

    @Override
    public Rule updateArtifactRuleConfig(String artifactId, RuleType ruleType, Rule ruleConfig) {
        return getInstance().updateArtifactRuleConfig(artifactId, ruleType, ruleConfig);
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType ruleType) {
        getInstance().deleteArtifactRule(artifactId, ruleType);
    }

    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType artifactType, InputStream data) {
        getInstance().testUpdateArtifact(artifactId, artifactType, data);
    }

    @Override
    public InputStream getArtifactByGlobalId(long globalId) {
        return getInstance().getArtifactByGlobalId(globalId);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {
        return getInstance().getArtifactMetaDataByGlobalId(globalId);
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType ruleType) {
        return getInstance().getGlobalRuleConfig(ruleType);
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType ruleType, Rule data) {
        return getInstance().updateGlobalRuleConfig(ruleType, data);
    }

    @Override
    public void deleteGlobalRule(RuleType ruleType) {
        getInstance().deleteGlobalRule(ruleType);
    }

    @Override
    public List<RuleType> listGlobalRules() {
        return getInstance().listGlobalRules();
    }

    @Override
    public void createGlobalRule(Rule data) {
        getInstance().createGlobalRule(data);
    }

    @Override
    public void deleteAllGlobalRules() {
        getInstance().deleteAllGlobalRules();
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, SearchOver over, SortOrder order, Integer offset, Integer limit) {
        return getInstance().searchArtifacts(search, over, order, offset, limit);
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {
        return getInstance().searchVersions(artifactId, offset, limit);
    }

}