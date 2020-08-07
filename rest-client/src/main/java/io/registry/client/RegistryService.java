package io.registry.client;

import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;

import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.List;

interface RegistryService {

    List<String> listArtifacts();


    ArtifactMetaData createArtifact(ArtifactType xRegistryArtifactType, String xRegistryArtifactId, IfExistsType ifExists, InputStream data);


    Response getLatestArtifact(String artifactId);


    ArtifactMetaData updateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data);


    void deleteArtifact(String artifactId);


    void updateArtifactState(String artifactId, UpdateState data);


    ArtifactMetaData getArtifactMetaData(String artifactId);


    void updateArtifactMetaData(String artifactId, EditableMetaData data);


    ArtifactMetaData getArtifactMetaDataByContent(String artifactId, InputStream data);


    List<Long> listArtifactVersions(String artifactId);


    VersionMetaData createArtifactVersion(String artifactId, ArtifactType xRegistryArtifactType, InputStream data);


    Response getArtifactVersion(Integer version, String artifactId);


    void updateArtifactVersionState(Integer version, String artifactId, UpdateState data);


    VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId);


    void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data);


    void deleteArtifactVersionMetaData(Integer version, String artifactId);


    List<RuleType> listArtifactRules(String artifactId);


    void createArtifactRule(String artifactId, Rule data);


    void deleteArtifactRules(String artifactId);


    Rule getArtifactRuleConfig(RuleType rule, String artifactId);


    Rule updateArtifactRuleConfig(RuleType rule, String artifactId, Rule data);


    void deleteArtifactRule(RuleType rule, String artifactId);


    void testUpdateArtifact(String artifactId, ArtifactType xRegistryArtifactType, InputStream data);


    Response getArtifactByGlobalId(long globalId);


    ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId);


    Rule getGlobalRuleConfig(RuleType rule);


    Rule updateGlobalRuleConfig(RuleType rule, Rule data);


    void deleteGlobalRule(RuleType rule);


    List<RuleType> listGlobalRules();


    void createGlobalRule(Rule data);


    void deleteAllGlobalRules();


    ArtifactSearchResults searchArtifacts(String search, Integer offset, Integer limit, SearchOver over, SortOrder order);


    VersionSearchResults searchVersions(String artifactId, Integer offset, Integer limit);

}
