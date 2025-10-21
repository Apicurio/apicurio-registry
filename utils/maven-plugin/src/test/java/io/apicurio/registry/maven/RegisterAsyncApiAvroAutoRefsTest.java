package io.apicurio.registry.maven;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import io.apicurio.registry.rest.v3.beans.IfArtifactExists;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RegisterAsyncApiAvroAutoRefsTest {

    private RegisterRegistryMojo mojo;
    private final File examplesRoot = Paths.get("../../examples/").toAbsolutePath().toFile();
    private WireMockServer wireMockServer;
    private Set<String> registeredArtifacts = new HashSet<>();

    @BeforeEach public void setup() {
        // Start WireMock server with custom transformer
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()
                .extensions(new CaptureRequestTransformer()));
        wireMockServer.start();

        // Mock artifact registration endpoint with debugging transformer
        wireMockServer.stubFor(post(urlPathMatching("/apis/registry/v3/groups/.*/artifacts")).willReturn(
                aResponse().withTransformers("capture-request-transformer").withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        mojo = new RegisterRegistryMojo();
        mojo.setRegistryUrl(wireMockServer.baseUrl() + "/apis/registry/v3");

        // clear captured registered artifacts
        registeredArtifacts.clear();
    }

    @AfterEach public void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test public void testAsyncApiWithAvroAutoRefs() throws Exception {
        File exampleDir = new File(examplesRoot, "asyncapi-avro-maven-with-references-auto");
        File asyncApiFile = new File(exampleDir, "src/main/resources/schemas/asyncapi-avro-refs.yml");

        // Create the artifact configuration
        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setGroupId("asyncapi-avro-maven-with-references-auto");
        artifact.setArtifactId("CustomersExample");
        artifact.setVersion("1.0.0");
        artifact.setArtifactType(ArtifactType.ASYNCAPI);
        artifact.setFile(asyncApiFile);
        artifact.setIfExists(IfArtifactExists.FIND_OR_CREATE_VERSION);
        artifact.setCanonicalize(true);
        artifact.setAutoRefs(true);

        mojo.setArtifacts(java.util.Collections.singletonList(artifact));
        mojo.execute();

        assertEquals(6, registeredArtifacts.size());
        assertTrue(registeredArtifacts.contains("asyncapi-avro-maven-with-references-auto:CustomersExample"));
        assertTrue(registeredArtifacts.contains(
                "asyncapi-avro-maven-with-references-auto:io.example.api.dtos.CustomerEvent"));
        assertTrue(registeredArtifacts.contains(
                "asyncapi-avro-maven-with-references-auto:io.example.api.dtos.Address"));
        assertTrue(registeredArtifacts.contains(
                "asyncapi-avro-maven-with-references-auto:io.example.api.dtos.PaymentMethod"));
        assertTrue(registeredArtifacts.contains(
                "asyncapi-avro-maven-with-references-auto:io.example.api.dtos.PaymentMethodType"));
        assertTrue(registeredArtifacts.contains(
                "asyncapi-avro-maven-with-references-auto:io.example.api.dtos.CustomerDeletedEvent"));

    }

    @Test public void testAvroAutoRefs() throws Exception {
        File exampleDir = new File(examplesRoot, "avro-maven-with-references-auto");
        File avroFile = new File(exampleDir, "src/main/resources/schemas/TradeRaw.avsc");

        // Create the artifact configuration
        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setGroupId("com.kubetrade.schema.trade");
        artifact.setArtifactId("TradeRaw");
        artifact.setVersion("2.0");
        artifact.setArtifactType(ArtifactType.AVRO);
        artifact.setFile(avroFile);
        artifact.setIfExists(IfArtifactExists.FIND_OR_CREATE_VERSION);
        artifact.setCanonicalize(true);
        artifact.setAutoRefs(true);

        mojo.setArtifacts(java.util.Collections.singletonList(artifact));
        mojo.execute();

        assertEquals(4, registeredArtifacts.size());
        assertTrue(registeredArtifacts.contains("com.kubetrade.schema.trade:TradeRaw"));
        assertTrue(registeredArtifacts.contains("com.kubetrade.schema.trade:TradeKey"));
        assertTrue(registeredArtifacts.contains("com.kubetrade.schema.trade:TradeValue"));
        assertTrue(registeredArtifacts.contains("com.kubetrade.schema.common:Exchange"));
    }

    @Test public void testAvroAnalyzeDirectory() throws Exception {
        File exampleDir = new File(examplesRoot, "avro-maven-with-references-auto");
        File avroFile = new File(exampleDir, "src/main/resources/schemas/TradeRaw.avsc");

        // Create the artifact configuration
        RegisterArtifact artifact = new RegisterArtifact();
        artifact.setGroupId("avro-maven-with-analyze-directory");
        artifact.setArtifactId("TradeRaw");
        artifact.setVersion("2.0");
        artifact.setArtifactType(ArtifactType.AVRO);
        artifact.setFile(avroFile);
        artifact.setIfExists(IfArtifactExists.FIND_OR_CREATE_VERSION);
        artifact.setCanonicalize(true);
        artifact.setAnalyzeDirectory(true);

        mojo.setArtifacts(java.util.Collections.singletonList(artifact));
        mojo.execute();

        assertEquals(4, registeredArtifacts.size());
        assertTrue(registeredArtifacts.contains("avro-maven-with-analyze-directory:TradeRaw"));
        assertTrue(registeredArtifacts.contains(
                "avro-maven-with-analyze-directory:com.kubetrade.schema.trade.TradeKey"));
        assertTrue(registeredArtifacts.contains(
                "avro-maven-with-analyze-directory:com.kubetrade.schema.trade.TradeValue"));
        assertTrue(registeredArtifacts.contains(
                "avro-maven-with-analyze-directory:com.kubetrade.schema.common.Exchange"));
    }

    // Custom transformer to capture/debug requests and generate dynamic responses
    class CaptureRequestTransformer extends ResponseTransformer {

        @Override public String getName() {
            return "capture-request-transformer";
        }

        @Override public Response transform(Request request, Response response, FileSource fileSource,
                Parameters parameters) {
            try {
                String responseBody = generateResponseBasedOnRequest(request);
                return Response.Builder.like(response).but().body(responseBody).build();
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

        }

        private String generateResponseBasedOnRequest(Request request) throws JsonProcessingException {
            String url = request.getUrl();
            String body = request.getBodyAsString();

            String groupId = extractGroupIdFromUrl(url);
            String artifactId = extractArtifactIdFromBody(body);

            registeredArtifacts.add(groupId + ":" + artifactId);

            return """
                    {
                      "version": {
                        "version": "1",
                        "globalId": 12345,
                        "contentId": 67890
                      },
                      "artifact": {
                        "groupId": "%s",
                        "artifactId": "%s"
                      }
                    }
                    """.formatted(groupId, artifactId);
        }

        private String extractGroupIdFromUrl(String url) {
            // Extract groupId from URL pattern: /apis/registry/v3/groups/{groupId}/artifacts
            String[] parts = url.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("groups".equals(parts[i]) && i + 1 < parts.length) {
                    return parts[i + 1];
                }
            }
            return "default";
        }

        private String extractArtifactIdFromBody(String body) throws JsonProcessingException {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(body);

            JsonNode artifactIdNode = jsonNode.get("artifactId");
            if (artifactIdNode != null && !artifactIdNode.isNull()) {
                return artifactIdNode.asText();
            }
            throw new RuntimeException("ArtifactId not found in request body");
        }

    }
}

