package io.registry;


import io.apicurio.registry.rest.beans.*;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.RuleType;
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
public class RegistryClient {

    private static final Logger log = Logger.getLogger(RegistryClient.class.getName());

    private ArtifactsService artifactsService;
    private SearchService searchService;
    private IdsService idsService;

    private RegistryClient(String baseUrl) {

        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .build();

        initServices(retrofit);
    }

    private RegistryClient(String baseUrl, OkHttpClient okHttpClient) {

        final Retrofit retrofit = new Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(baseUrl)
                .build();

        initServices(retrofit);
    }

    private void initServices(Retrofit retrofit) {
        artifactsService = retrofit.create(ArtifactsService.class);
        idsService = retrofit.create(IdsService.class);
        searchService = retrofit.create(SearchService.class);
    }

    public List<String> listArtifacts() {

        return artifactsService.listArtifacts();
    }

    public CompletionStage<ArtifactMetaData> createArtifact(ArtifactType artifactType, String artifactId, IfExistsType ifExistsType, InputStream data) {

        return artifactsService.createArtifact(artifactType, artifactId, ifExistsType, data);
    }

    public Response getLatestArtifact(String artifactId) {
        return artifactsService.getLatestArtifact(artifactId);
    }

    public CompletionStage<ArtifactMetaData> updateArtifact(String artifactId,
                                                            ArtifactType xRegistryArtifactType, InputStream data) {
        return artifactsService.updateArtifact(artifactId, xRegistryArtifactType, data);
    }

    public void deleteArtifact(String artifactId) {
        artifactsService.deleteArtifact(artifactId);
    }

    public void updateArtifactState(String artifactId, UpdateState data) {
        artifactsService.updateArtifactState(artifactId, data);
    }

    public ArtifactMetaData getArtifactMetaData(String artifactId) {
        return artifactsService.getArtifactMetaData(artifactId);
    }

    public void updateArtifactMetaData(String artifactId, EditableMetaData data) {
        artifactsService.updateArtifactMetaData(artifactId, data);
    }

    public ArtifactMetaData getArtifactMetaDataByContent(String artifactId,
                                                         InputStream data) {
        return artifactsService.getArtifactMetaDataByContent(artifactId, data);
    }

    public List<Long> listArtifactVersions(String artifactId) {
        return artifactsService.listArtifactVersions(artifactId);
    }

    public CompletionStage<VersionMetaData> createArtifactVersion(String artifactId,
                                                                  ArtifactType xRegistryArtifactType, InputStream data) {
        return artifactsService.createArtifactVersion(artifactId, xRegistryArtifactType, data);
    }

    public Response getArtifactVersion(Integer version,
                                       String artifactId) {
        return artifactsService.getArtifactVersion(version, artifactId);
    }

    public void updateArtifactVersionState(Integer version, String artifactId, UpdateState data) {
        artifactsService.updateArtifactVersionState(version, artifactId, data);
    }

    public VersionMetaData getArtifactVersionMetaData(Integer version, String artifactId) {
        return artifactsService.getArtifactVersionMetaData(version, artifactId);
    }

    public void updateArtifactVersionMetaData(Integer version, String artifactId, EditableMetaData data) {
        artifactsService.updateArtifactVersionMetaData(version, artifactId, data);
    }

    public void deleteArtifactVersionMetaData(Integer version, String artifactId) {
        artifactsService.deleteArtifactVersionMetaData(version, artifactId);
    }

    public List<RuleType> listArtifactRules(String artifactId) {
        return artifactsService.listArtifactRules(artifactId);
    }

    public void createArtifactRule(String artifactId, Rule data) {
        artifactsService.createArtifactRule(artifactId, data);
    }

    public void deleteArtifactRules(String artifactId) {
        artifactsService.deleteArtifactRules(artifactId);
    }

    public Rule getArtifactRuleConfig(RuleType rule,
                                      String artifactId) {
        return artifactsService.getArtifactRuleConfig(rule, artifactId);
    }

    public Rule updateArtifactRuleConfig(RuleType rule,
                                         String artifactId, Rule data) {
        return artifactsService.updateArtifactRuleConfig(rule, artifactId, data);
    }

    public void deleteArtifactRule(RuleType rule, String artifactId) {
        artifactsService.deleteArtifactRule(rule, artifactId);
    }

    public void testUpdateArtifact(String artifactId,
                                   ArtifactType xRegistryArtifactType, InputStream data) {
        artifactsService.testUpdateArtifact(artifactId, xRegistryArtifactType, data);
    }

    public Response getArtifactByGlobalId(long globalId) {
        return idsService.getArtifactByGlobalId(globalId);
    }

    public ArtifactMetaData getArtifactMetaDataByGlobalId(long globalId) {
        return idsService.getArtifactMetaDataByGlobalId(globalId);
    }

    public ArtifactSearchResults searchArtifacts(String search, Integer offset, Integer limit, SearchOver over, SortOrder order) {
        return searchService.searchArtifacts(search, offset, limit, over, order);
    }

    public VersionSearchResults searchVersion(String artifactId, Integer offset, Integer limit) {
        return searchService.searchVersions(artifactId, offset, limit);
    }
}
