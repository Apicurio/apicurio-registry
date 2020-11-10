package io.apicurio.registry.utils.tests;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

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

    private RegistryRestClient delegate;
    private String apiUrl;

    public LazyRegistryRestClient(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    private synchronized RegistryRestClient getDelegate() {
        if (delegate == null) {
            delegate = RegistryRestClientFactory.create(apiUrl);
        }
        return delegate;
    }

    @Override
    public void close() throws Exception {
        getDelegate().close();
    }

    @Override
    public List<String> listArtifacts(Map<String, String> headers) {
        return getDelegate().listArtifacts(headers);
    }
    
    @Override
    public ArtifactMetaData createArtifact(Map<String, String> headers, InputStream data) {
        return getDelegate().createArtifact(headers, data);
    }
    
    @Override
    public ArtifactMetaData createArtifact(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data) {
        return getDelegate().createArtifact(headers, artifactId, artifactType, data);
    }
    
    @Override
    public ArtifactMetaData createArtifact(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data,
            IfExistsType ifExists, Boolean canonical) {
        return getDelegate().createArtifact(headers, artifactId, artifactType, data, ifExists, canonical);
    }

    @Override
    public InputStream getLatestArtifact(Map<String, String> headers, String artifactId) {
        return getDelegate().getLatestArtifact(headers, artifactId);
    }

    @Override
    public ArtifactMetaData updateArtifact(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data) {
        return getDelegate().updateArtifact(headers, artifactId, artifactType, data);
    }

    @Override
    public void deleteArtifact(Map<String, String> headers, String artifactId) {
        getDelegate().deleteArtifact(headers, artifactId);
    }

    @Override
    public void updateArtifactState(Map<String, String> headers, String artifactId, UpdateState newState) {
        getDelegate().updateArtifactState(headers, artifactId, newState);
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(Map<String, String> headers, String artifactId) {
        return getDelegate().getArtifactMetaData(headers, artifactId);
    }

    @Override
    public void updateArtifactMetaData(Map<String, String> headers, String artifactId, EditableMetaData metaData) {
        getDelegate().updateArtifactMetaData(headers, artifactId, metaData);
    }
    
    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(Map<String, String> headers, String artifactId, Boolean canonical,
            InputStream data) {
        return getDelegate().getArtifactMetaDataByContent(headers, artifactId, canonical, data);
    }

    @Override
    public List<Long> listArtifactVersions(Map<String, String> headers, String artifactId) {
        return getDelegate().listArtifactVersions(headers, artifactId);
    }

    @Override
    public VersionMetaData createArtifactVersion(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data) {
        return getDelegate().createArtifactVersion(headers, artifactId, artifactType, data);
    }

    @Override
    public InputStream getArtifactVersion(Map<String, String> headers, String artifactId, Integer version) {
        return getDelegate().getArtifactVersion(headers, artifactId, version);
    }

    @Override
    public void updateArtifactVersionState(Map<String, String> headers, String artifactId, Integer version, UpdateState newState) {
        getDelegate().updateArtifactVersionState(headers, artifactId, version, newState);
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(Map<String, String> headers, String artifactId, Integer version) {
        return getDelegate().getArtifactVersionMetaData(headers, artifactId, version);
    }

    @Override
    public void updateArtifactVersionMetaData(Map<String, String> headers, String artifactId, Integer version, EditableMetaData metaData) {
        getDelegate().updateArtifactVersionMetaData(headers, artifactId, version, metaData);
    }

    @Override
    public void deleteArtifactVersionMetaData(Map<String, String> headers, String artifactId, Integer version) {
        getDelegate().deleteArtifactVersionMetaData(headers, artifactId, version);
    }

    @Override
    public List<RuleType> listArtifactRules(Map<String, String> headers, String artifactId) {
        return getDelegate().listArtifactRules(headers, artifactId);
    }

    @Override
    public void createArtifactRule(Map<String, String> headers, String artifactId, Rule ruleConfig) {
        getDelegate().createArtifactRule(headers, artifactId, ruleConfig);
    }

    @Override
    public void deleteArtifactRules(Map<String, String> headers, String artifactId) {
        getDelegate().deleteArtifactRules(headers, artifactId);
    }

    @Override
    public Rule getArtifactRuleConfig(Map<String, String> headers, String artifactId, RuleType ruleType) {
        return getDelegate().getArtifactRuleConfig(headers, artifactId, ruleType);
    }

    @Override
    public Rule updateArtifactRuleConfig(Map<String, String> headers, String artifactId, RuleType ruleType, Rule ruleConfig) {
        return getDelegate().updateArtifactRuleConfig(headers, artifactId, ruleType, ruleConfig);
    }

    @Override
    public void deleteArtifactRule(Map<String, String> headers, String artifactId, RuleType ruleType) {
        getDelegate().deleteArtifactRule(headers, artifactId, ruleType);
    }

    @Override
    public void testUpdateArtifact(Map<String, String> headers, String artifactId, ArtifactType artifactType, InputStream data) {
        getDelegate().testUpdateArtifact(headers, artifactId, artifactType, data);
    }

    @Override
    public InputStream getArtifactByGlobalId(Map<String, String> headers, long globalId) {
        return getDelegate().getArtifactByGlobalId(headers, globalId);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(Map<String, String> headers, long globalId) {
        return getDelegate().getArtifactMetaDataByGlobalId(headers, globalId);
    }

    @Override
    public Rule getGlobalRuleConfig(Map<String, String> headers, RuleType ruleType) {
        return getDelegate().getGlobalRuleConfig(headers, ruleType);
    }

    @Override
    public Rule updateGlobalRuleConfig(Map<String, String> headers, RuleType ruleType, Rule data) {
        return getDelegate().updateGlobalRuleConfig(headers, ruleType, data);
    }

    @Override
    public void deleteGlobalRule(Map<String, String> headers, RuleType ruleType) {
        getDelegate().deleteGlobalRule(headers, ruleType);
    }

    @Override
    public List<RuleType> listGlobalRules(Map<String, String> headers) {
        return getDelegate().listGlobalRules(headers);
    }

    @Override
    public void createGlobalRule(Map<String, String> headers, Rule data) {
        getDelegate().createGlobalRule(headers, data);
    }

    @Override
    public void deleteAllGlobalRules(Map<String, String> headers) {
        getDelegate().deleteAllGlobalRules(headers);
    }

    @Override
    public ArtifactSearchResults searchArtifacts(Map<String, String> headers, String search, SearchOver over, SortOrder order, Integer offset, Integer limit) {
        return getDelegate().searchArtifacts(headers, search, over, order, offset, limit);
    }

    @Override
    public VersionSearchResults searchVersions(Map<String, String> headers, String artifactId, Integer offset, Integer limit) {
        return getDelegate().searchVersions(headers, artifactId, offset, limit);
    }
}