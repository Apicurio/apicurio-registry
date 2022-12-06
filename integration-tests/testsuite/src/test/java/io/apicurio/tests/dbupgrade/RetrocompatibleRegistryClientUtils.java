package io.apicurio.tests.dbupgrade;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.impl.ErrorHandler;
import io.apicurio.registry.rest.client.impl.RegistryClientImpl;
import io.apicurio.registry.rest.client.request.Parameters;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.rest.v2.beans.ArtifactReference;
import io.apicurio.registry.rest.v2.beans.ArtifactSearchResults;
import io.apicurio.registry.rest.v2.beans.ContentCreateRequest;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.rest.v2.beans.SearchedArtifact;
import io.apicurio.registry.rest.v2.beans.SortBy;
import io.apicurio.registry.rest.v2.beans.SortOrder;
import io.apicurio.registry.rest.v2.beans.VersionMetaData;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.request.Operation;
import io.apicurio.rest.client.request.Request;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.apicurio.tests.multitenancy.TenantUserClient;

import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.rest.client.AdminClientFactory.BASE_PATH;

// This class is a plain hack, in https://github.com/Apicurio/apicurio-registry/pull/3006
// we fixed a return type of the public API, but, some tests in this package, are running an old version of Registry
// we do not care about dates here, but the deserializer is going to fail finding an incompatible format
// here we are overriding each and every client method used by the mentioned tests so that the dates are discarded.
// I'm sorry :-(
public class RetrocompatibleRegistryClientUtils {

    @JsonIgnoreProperties(value={ "createdOn", "modifiedOn" })
    public static class TestArtifactMetadata extends ArtifactMetaData { }

    @JsonIgnoreProperties(value={ "createdOn" })
    public static class TestVersionMetadata extends VersionMetaData { }

    @JsonIgnoreProperties(value={ "createdOn", "modifiedOn" })
    public static class TestSearchedArtifact extends SearchedArtifact {}

    public static class TestArtifactSearchResults extends ArtifactSearchResults {
        @JsonProperty("artifacts")
        @JsonPropertyDescription("The artifacts returned in the result set.")
        @JsonDeserialize(contentAs = TestSearchedArtifact.class)
        private List<SearchedArtifact> artifacts = new ArrayList<SearchedArtifact>();

        @Override
        @JsonProperty("artifacts")
        public List<SearchedArtifact> getArtifacts() {
            return artifacts;
        }

        @Override
        @JsonProperty("artifacts")
        public void setArtifacts(List<SearchedArtifact> artifacts) {
            this.artifacts = artifacts;
        }
    }

    private static Request<TestArtifactMetadata> createArtifactWithReferencesRequest(String groupId, Map<String, String> headers, ContentCreateRequest data, Map<String, List<String>> queryParams)
            throws JsonProcessingException {
        return new Request.RequestBuilder<TestArtifactMetadata>()
                .operation(Operation.POST)
                .path("groups/%s/artifacts")
                .headers(headers)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseType(new TypeReference<TestArtifactMetadata>() {
                })
                .data(IoUtil.toStream(new ObjectMapper().writeValueAsBytes(data)))
                .build();
    }

    private static Request<TestArtifactMetadata> createArtifactRequest(String groupId, Map<String, String> headers, InputStream data, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<TestArtifactMetadata>()
                .operation(Operation.POST)
                .path("groups/%s/artifacts")
                .headers(headers)
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseType(new TypeReference<TestArtifactMetadata>() {
                })
                .data(data)
                .build();
    }

    private static Request<TestVersionMetadata> getArtifactVersionMetaDataByContentRequest(String groupId, String artifactId, Map<String, String> headers, Map<String, List<String>> queryParams, InputStream data) {
        return new Request.RequestBuilder<TestVersionMetadata>()
                .operation(Operation.POST)
                .path("groups/%s/artifacts/%s/meta")
                .headers(headers)
                .pathParams(List.of(groupId, artifactId))
                .queryParams(queryParams)
                .responseType(new TypeReference<TestVersionMetadata>() {
                })
                .data(data)
                .build();
    }

    private static Request<TestArtifactSearchResults> listArtifactsInGroupRequest(String groupId, Map<String, List<String>> queryParams) {
        return new Request.RequestBuilder<TestArtifactSearchResults>()
                .operation(Operation.GET)
                .path("groups/%s/artifacts")
                .pathParams(List.of(groupId))
                .queryParams(queryParams)
                .responseType(new TypeReference<TestArtifactSearchResults>() {
                })
                .build();
    }

    private static Request<TestVersionMetadata> getArtifactVersionMetaDataRequest(String groupId, String artifactId, String version) {
        return new Request.RequestBuilder<TestVersionMetadata>()
                .operation(Operation.GET)
                .path("groups/%s/artifacts/%s/versions/%s/meta")
                .pathParams(List.of(groupId, artifactId, version))
                .responseType(new TypeReference<TestVersionMetadata>() {
                })
                .build();
    }

    private static class HackRegistryClientImpl extends RegistryClientImpl {

        public HackRegistryClientImpl(ApicurioHttpClient apicurioHttpClient) {
            super(apicurioHttpClient);
        }

        @Override
        public VersionMetaData getArtifactVersionMetaData(String groupId, String artifactId, String version) {
            return apicurioHttpClient.sendRequest(getArtifactVersionMetaDataRequest(normalizeGid(groupId), artifactId, version));
        }

        @Override
        public TestArtifactSearchResults listArtifactsInGroup(String groupId, SortBy orderBy, SortOrder order, Integer offset, Integer limit) {
            final Map<String, List<String>> queryParams = new HashMap<>();
            checkCommonQueryParams(orderBy, order, limit, offset, queryParams);
            return apicurioHttpClient.sendRequest(listArtifactsInGroupRequest(normalizeGid(groupId), queryParams));
        }

        @Override
        public VersionMetaData getArtifactVersionMetaDataByContent(String groupId, String artifactId, Boolean canonical, String contentType, InputStream data) {
            final Map<String, List<String>> queryParams = canonical != null ? Map.of(Parameters.CANONICAL, Collections.singletonList(String.valueOf(canonical))) : Collections.emptyMap();
            final Map<String, String> headers = contentType != null ? Map.of(Headers.CONTENT_TYPE, contentType) : Collections.emptyMap();
            return apicurioHttpClient.sendRequest(getArtifactVersionMetaDataByContentRequest(normalizeGid(groupId), artifactId, headers, queryParams, data));
        }

        @Override
        public ArtifactMetaData createArtifact(String groupId, String artifactId, String version, String artifactType, IfExists ifExists, Boolean canonical, String artifactName, String artifactDescription, String contentType, String fromURL, String artifactSHA, InputStream data) {
            var ca = createArtifactLogic(artifactId, version, artifactType, ifExists, canonical, artifactName, artifactDescription, contentType, artifactSHA);

            if (fromURL != null) {
                ca.headers.put(Headers.CONTENT_TYPE, ContentTypes.APPLICATION_CREATE_EXTENDED);
                data = new StringBufferInputStream("{ \"content\" : \"" + fromURL + "\" }");
            }

            return apicurioHttpClient.sendRequest(createArtifactRequest(normalizeGid(groupId), ca.headers, data, ca.queryParams));
        }

        @Override
        public ArtifactMetaData createArtifact(String groupId, String artifactId, String version, String artifactType, IfExists ifExists, Boolean canonical, String artifactName, String artifactDescription, String contentType, String fromURL, String artifactSHA, InputStream data, List<ArtifactReference> artifactReferences) {
            var ca = createArtifactLogic(artifactId, version, artifactType, ifExists, canonical, artifactName, artifactDescription, ContentTypes.APPLICATION_CREATE_EXTENDED, artifactSHA);

            String content = IoUtil.toString(data);
            if (fromURL != null) {
                content = " { \"content\" : \"" + fromURL + "\" }";
            }

            final ContentCreateRequest contentCreateRequest = new ContentCreateRequest();
            contentCreateRequest.setContent(content);
            contentCreateRequest.setReferences(artifactReferences);

            try {
                return apicurioHttpClient.sendRequest(createArtifactWithReferencesRequest(normalizeGid(groupId), ca.headers, contentCreateRequest, ca.queryParams));
            } catch (JsonProcessingException e) {
                throw parseSerializationError(e);
            }
        }
    }

    public static TenantUserClient create(TenantUserClient tuc) {
        var client = new HackRegistryClientImpl(ApicurioHttpClientFactory.create(tuc.tenantAppUrl + "/" + BASE_PATH, Collections.emptyMap(), new OidcAuth(ApicurioHttpClientFactory.create(tuc.tokenEndpoint, new AuthErrorHandler()), tuc.user.principalId, tuc.user.principalPassword), new AuthErrorHandler()));

        return new TenantUserClient(
                tuc.user,
                tuc.tenantAppUrl,
                client,
                tuc.tokenEndpoint
        );
    }

    public static RegistryClient create(String url) {
        return new HackRegistryClientImpl(ApicurioHttpClientFactory.create(url + BASE_PATH, Map.of(), null, new ErrorHandler()));
    }

}
