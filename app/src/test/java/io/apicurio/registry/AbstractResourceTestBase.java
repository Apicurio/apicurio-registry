package io.apicurio.registry;

import static org.hamcrest.Matchers.equalTo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.RequestAdapter;

import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.v3.V3ApiUtil;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.storage.dto.ArtifactReferenceDto;
import io.apicurio.registry.types.ArtifactMediaTypes;
import io.apicurio.registry.types.ArtifactState;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.response.ValidatableResponse;

/**
 * Abstract base class for all tests that test via the jax-rs layer.
 *
 */
@TestInstance(Lifecycle.PER_CLASS)
public abstract class AbstractResourceTestBase extends AbstractRegistryTestBase {

    public static final String CT_JSON = "application/json";
    protected static final String CT_PROTO = "application/x-protobuf";
    protected static final String CT_YAML = "application/x-yaml";
    protected static final String CT_XML = "application/xml";
    public static final String CT_JSON_EXTENDED = "application/create.extended+json";

    public String registryApiBaseUrl;
    protected String registryV3ApiUrl;
    protected RegistryClient clientV3;
    protected RestService confluentClient;


    @BeforeAll
    protected void beforeAll() throws Exception {
        String serverUrl = "http://localhost:%s/apis";
        registryApiBaseUrl = String.format(serverUrl, testPort);
        registryV3ApiUrl = registryApiBaseUrl + "/registry/v3";
        clientV3 = createRestClientV3();
        confluentClient = buildConfluentClient();
    }

    @AfterAll
    protected void afterAll() {
        //delete data to
        //storage.deleteAllUserData();
    }

    protected RestService buildConfluentClient() {
        return new RestService("http://localhost:" + testPort + "/apis/ccompat/v7");
    }

    protected final RequestAdapter anonymousAdapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
    
    protected RegistryClient createRestClientV3() {
        anonymousAdapter.setBaseUrl(registryV3ApiUrl);
        var client = new RegistryClient(anonymousAdapter);
        return client;
    }

    @BeforeEach
    protected void beforeEach() throws Exception {
        setupRestAssured();
        deleteGlobalRules(0);
    }

    protected void setupRestAssured() {
        RestAssured.baseURI = registryApiBaseUrl;
        RestAssured.registerParser(ArtifactMediaTypes.BINARY.toString(), Parser.JSON);
    }

    protected void deleteGlobalRules(int expectedDefaultRulesCount) throws Exception {
        // Delete all global rules
        TestUtils.retry(() -> {
            try {
                clientV3.admin().rules().delete();
            } catch (Exception err) {
                // ignore
            }
            Assertions.assertEquals(expectedDefaultRulesCount, clientV3.admin().rules().get().size());
        });
    }

    protected Long createArtifact(String artifactId, String artifactType, String artifactContent) throws Exception {
        return createArtifact(GroupId.DEFAULT.getRawGroupIdWithDefaultString(), artifactId, artifactType, artifactContent);
    }


    protected Long createArtifact(String groupId, String artifactId, String artifactType, String artifactContent) throws Exception {
        var content = new io.apicurio.registry.rest.client.models.ArtifactContent();
        content.setContent(artifactContent);
        var result = clientV3
                .groups()
                .byGroupId(groupId)
                .artifacts()
                .post(content, config -> {
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("X-Registry-ArtifactType", artifactType);
                })
                ;

        assert( result.getArtifactId().equals(artifactId) );
        assert( result.getType().equals(artifactType) );

        return result.getGlobalId();
    }

    protected Long createArtifactWithReferences(String groupId, String artifactId, String artifactType, String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        var response = createArtifactExtendedRaw(groupId, artifactId, artifactType, artifactContent, artifactReferences);

        assert( response.getType().equals(artifactType) );
        assert( response.getArtifactId().equals(artifactId) );

        return response.getGlobalId();
    }

    protected Long updateArtifactWithReferences(String groupId, String artifactId, String artifactType, String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        var response = createArtifactVersionExtendedRaw(groupId, artifactId, artifactType, artifactContent, artifactReferences);

        assert( response.getType().equals(artifactType) );
        assert( response.getArtifactId().equals(artifactId) );

        return response.getGlobalId();
    }

    protected VersionMetaData createArtifactExtendedRaw(String groupId, String artifactId, String artifactType, String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        var content = new io.apicurio.registry.rest.client.models.ArtifactContent();
        content.setContent(artifactContent);
        if (artifactReferences != null) {
            var references = artifactReferences.stream().map(r -> {
                var ref = new io.apicurio.registry.rest.client.models.ArtifactReference();
                ref.setArtifactId(r.getArtifactId());
                ref.setGroupId(r.getGroupId());
                ref.setVersion(r.getVersion());
                ref.setName(r.getName());
                return ref;
            }).collect(Collectors.toList());
            content.setReferences(references);
        }

        return clientV3
                .groups()
                .byGroupId(groupId)
                .artifacts()
                .post(content, config -> {
                    if (artifactId != null) {
                        config.headers.add("X-Registry-ArtifactId", artifactId);
                    }
                    if (artifactType != null) {
                        config.headers.add("X-Registry-ArtifactType", artifactType);
                    }
                })
                ;
    }

    protected VersionMetaData createArtifactVersionExtendedRaw(String groupId, String artifactId, String artifactType, 
            String artifactContent, List<ArtifactReference> artifactReferences) throws Exception {

        var contentCreateRequest = new io.apicurio.registry.rest.client.models.ArtifactContent();
        contentCreateRequest.setContent(artifactContent);
        var references = artifactReferences.stream().map(r -> {
            var ref = new io.apicurio.registry.rest.client.models.ArtifactReference();
            ref.setArtifactId(r.getArtifactId());
            ref.setGroupId(r.getGroupId());
            ref.setVersion(r.getVersion());
            ref.setName(r.getName());
            return ref;
        }).collect(Collectors.toList());
        contentCreateRequest.setReferences(references);

        return clientV3
                .groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .post(contentCreateRequest, config -> {
                    config.headers.add("X-Registry-ArtifactId", artifactId);
                    config.headers.add("X-Registry-ArtifactType", artifactType);
                })
                ;
    }

    protected Long createArtifactVersion(String artifactId, String artifactType, String artifactContent) throws Exception {
        return createArtifactVersion(GroupId.DEFAULT.getRawGroupIdWithDefaultString(), artifactId, artifactType, artifactContent);
    }

    protected Long createArtifactVersion(String groupId, String artifactId, String artifactType, String artifactContent) throws Exception {
        var content = new io.apicurio.registry.rest.client.models.ArtifactContent();
        content.setContent(artifactContent);

        var version = clientV3
                .groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .versions()
                .post(content, config -> {config.headers.add("X-Registry-ArtifactType", artifactType); })
                ;

        assert( version.getArtifactId().equals(artifactId) );
        assert( version.getType().equals(artifactType) );

        return version.getGlobalId();
    }

    protected void createArtifactRule(String groupId, String artifactId, RuleType ruleType, String ruleConfig) {
        var rule = new io.apicurio.registry.rest.client.models.Rule();
        rule.setConfig(ruleConfig);
        rule.setType(io.apicurio.registry.rest.client.models.RuleType.forValue(ruleType.value()));

        clientV3
                .groups()
                .byGroupId(groupId)
                .artifacts()
                .byArtifactId(artifactId)
                .rules()
                .post(rule);
    }

    protected io.apicurio.registry.rest.client.models.Rule createGlobalRule(RuleType ruleType, String ruleConfig) {
        var rule = new io.apicurio.registry.rest.client.models.Rule();
        rule.setConfig(ruleConfig);
        rule.setType(io.apicurio.registry.rest.client.models.RuleType.forValue(ruleType.value()));

        clientV3
            .admin()
            .rules()
            .post(rule);
        // TODO: verify this get
        return clientV3
                .admin()
                .rules()
                .byRule(ruleType.value())
                .get();
    }

    /**
     * Ensures the state of the meta-data response is what we expect.
     *
     * @param response
     * @param state
     */
    protected void validateMetaDataResponseState(ValidatableResponse response, ArtifactState state, boolean version) {
        response.statusCode(200);
        response.body("state", equalTo(state.name()));
    }

    protected String getRandomValidJsonSchemaContent() {
        return "{\n" +
                "  \"$id\": \"https://example.com/person.schema.json\",\n" +
                "  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n" +
                "  \"title\": \"Person-" + UUID.randomUUID() + "\",\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "  }\n" +
                "}";
    }

    protected byte[] concatContentAndReferences(byte[] contentBytes, String references) throws IOException {
        if (references != null) {
            final byte[] referencesBytes = references.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(contentBytes.length + referencesBytes.length);
            outputStream.write(contentBytes);
            outputStream.write(referencesBytes);
            return outputStream.toByteArray();
        } else {
            return contentBytes;
        }
    }

    protected List<ArtifactReferenceDto> toReferenceDtos(List<ArtifactReference> references) {
        if (references == null) {
            references = Collections.emptyList();
        }
        return references.stream()
                .peek(r -> r.setGroupId(new GroupId(r.getGroupId()).getRawGroupIdWithNull()))
                .map(V3ApiUtil::referenceToDto)
                .collect(Collectors.toList());
    }

    protected void assertForbidden(Exception exception) {
        Assertions.assertEquals(ApiException.class, exception.getClass());
        Assertions.assertEquals(403, ((ApiException)exception).getResponseStatusCode());
    }

    protected void assertNotAuthorized(Exception exception) {
        if (exception instanceof  NotAuthorizedException) {
            // thrown by the token provider adapter
        } else {
            // mapped by Kiota
            Assertions.assertEquals(ApiException.class, exception.getClass());
            Assertions.assertEquals(401, ((ApiException) exception).getResponseStatusCode());
        }
    }

}
