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
    public List<String> listArtifacts() {
        return getDelegate().listArtifacts();
    }
    
    @Override
    public ArtifactMetaData createArtifact(InputStream data) {
        return getDelegate().createArtifact(data);
    }
    
    @Override
    public ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, InputStream data) {
        return getDelegate().createArtifact(artifactId, artifactType, data);
    }
    
    @Override
    public ArtifactMetaData createArtifact(String artifactId, ArtifactType artifactType, InputStream data,
            IfExistsType ifExists, Boolean canonical) {
        return getDelegate().createArtifact(artifactId, artifactType, data, ifExists, canonical);
    }

    @Override
    public InputStream getLatestArtifact(String artifactId) {
        return getDelegate().getLatestArtifact(artifactId);
    }

    @Override
    public ArtifactMetaData updateArtifact(String artifactId, ArtifactType artifactType, InputStream data) {
        return getDelegate().updateArtifact(artifactId, artifactType, data);
    }

    @Override
    public void deleteArtifact(String artifactId) {
        getDelegate().deleteArtifact(artifactId);
    }

    @Override
    public void updateArtifactState(String artifactId, UpdateState newState) {
        getDelegate().updateArtifactState(artifactId, newState);
    }

    @Override
    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        return getDelegate().getArtifactMetaData(artifactId);
    }

    @Override
    public void updateArtifactMetaData(String artifactId, EditableMetaData metaData) {
        getDelegate().updateArtifactMetaData(artifactId, metaData);
    }
    
    @Override
    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId, Boolean canonical,
            InputStream data) {
        return getDelegate().getArtifactMetaDataByContent(artifactId, canonical, data);
    }

    @Override
    public List<Long> listArtifactVersions(String artifactId) {
        return getDelegate().listArtifactVersions(artifactId);
    }

    @Override
    public VersionMetaData createArtifactVersion(String artifactId, ArtifactType artifactType, InputStream data) {
        return getDelegate().createArtifactVersion(artifactId, artifactType, data);
    }

    @Override
    public InputStream getArtifactVersion(String artifactId, Integer version) {
        return getDelegate().getArtifactVersion(artifactId, version);
    }

    @Override
    public void updateArtifactVersionState(String artifactId, Integer version, UpdateState newState) {
        getDelegate().updateArtifactVersionState(artifactId, version, newState);
    }

    @Override
    public VersionMetaData getArtifactVersionMetaData(String artifactId, Integer version) {
        return getDelegate().getArtifactVersionMetaData(artifactId, version);
    }

    @Override
    public void updateArtifactVersionMetaData(String artifactId, Integer version, EditableMetaData metaData) {
        getDelegate().updateArtifactVersionMetaData(artifactId, version, metaData);
    }

    @Override
    public void deleteArtifactVersionMetaData(String artifactId, Integer version) {
        getDelegate().deleteArtifactVersionMetaData(artifactId, version);
    }

    @Override
    public List<RuleType> listArtifactRules(String artifactId) {
        return getDelegate().listArtifactRules(artifactId);
    }

    @Override
    public void createArtifactRule(String artifactId, Rule ruleConfig) {
        getDelegate().createArtifactRule(artifactId, ruleConfig);
    }

    @Override
    public void deleteArtifactRules(String artifactId) {
        getDelegate().deleteArtifactRules(artifactId);
    }

    @Override
    public Rule getArtifactRuleConfig(String artifactId, RuleType ruleType) {
        return getDelegate().getArtifactRuleConfig(artifactId, ruleType);
    }

    @Override
    public Rule updateArtifactRuleConfig(String artifactId, RuleType ruleType, Rule ruleConfig) {
        return getDelegate().updateArtifactRuleConfig(artifactId, ruleType, ruleConfig);
    }

    @Override
    public void deleteArtifactRule(String artifactId, RuleType ruleType) {
        getDelegate().deleteArtifactRule(artifactId, ruleType);
    }

    @Override
    public void testUpdateArtifact(String artifactId, ArtifactType artifactType, InputStream data) {
        getDelegate().testUpdateArtifact(artifactId, artifactType, data);
    }

    @Override
    public InputStream getArtifactByGlobalId(long globalId) {
        return getDelegate().getArtifactByGlobalId(globalId);
    }

    @Override
    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {
        return getDelegate().getArtifactMetaDataByGlobalId(globalId);
    }

    @Override
    public Rule getGlobalRuleConfig(RuleType ruleType) {
        return getDelegate().getGlobalRuleConfig(ruleType);
    }

    @Override
    public Rule updateGlobalRuleConfig(RuleType ruleType, Rule data) {
        return getDelegate().updateGlobalRuleConfig(ruleType, data);
    }

    @Override
    public void deleteGlobalRule(RuleType ruleType) {
        getDelegate().deleteGlobalRule(ruleType);
    }

    @Override
    public List<RuleType> listGlobalRules() {
        return getDelegate().listGlobalRules();
    }

    @Override
    public void createGlobalRule(Rule data) {
        getDelegate().createGlobalRule(data);
    }

    @Override
    public void deleteAllGlobalRules() {
        getDelegate().deleteAllGlobalRules();
    }

    @Override
    public ArtifactSearchResults searchArtifacts(String search, SearchOver over, SortOrder order, Integer offset, Integer limit) {
        return getDelegate().searchArtifacts(search, over, order, offset, limit);
    }

    @Override
    public VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit) {
        return getDelegate().searchVersions(artifactId, offset, limit);
    }

}