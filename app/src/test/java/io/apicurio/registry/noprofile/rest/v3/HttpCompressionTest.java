package io.apicurio.registry.noprofile.rest.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.beans.CreateVersion;
import io.apicurio.registry.rest.v3.beans.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.config.DecoderConfig;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the HTTP compression enabled via quarkus.http.enable-compression /
 * quarkus.http.enable-decompression actually takes effect on the wire, rather than just being
 * configured. RestAssured's underlying HTTP client auto-decompresses gzip responses by default,
 * which would hide a broken configuration, so these tests disable that auto-decompression to
 * inspect the raw bytes and headers.
 */
@QuarkusTest
public class HttpCompressionTest extends AbstractResourceTestBase {

    private static final String GROUP = "HttpCompressionTest";

    private static final RestAssuredConfig NO_AUTO_DECODE = RestAssuredConfig.newConfig()
            .decoderConfig(DecoderConfig.decoderConfig().noContentDecoders());

    /**
     * Repeated content compresses well and is large enough that gzip's fixed overhead doesn't
     * dominate, so a real compression benefit shows up in the byte counts.
     */
    private static String largeJsonSchemaContent() {
        StringBuilder properties = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            properties.append("\"field").append(i).append("\": { \"type\": \"string\", ")
                    .append("\"description\": \"A repeated filler field used to pad this schema ")
                    .append("so compression has enough redundant content to work with.\" },");
        }
        properties.setLength(properties.length() - 1);
        return "{ \"type\": \"object\", \"properties\": { " + properties + " } }";
    }

    private static byte[] gzip(byte[] input) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(input);
        }
        return baos.toByteArray();
    }

    private static byte[] gunzip(byte[] input) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPInputStream gzipIn = new GZIPInputStream(new java.io.ByteArrayInputStream(input))) {
            gzipIn.transferTo(baos);
        }
        return baos.toByteArray();
    }

    @Test
    public void testResponseIsCompressedWhenClientAcceptsGzip() throws Exception {
        String artifactId = "testResponseIsCompressedWhenClientAcceptsGzip";
        String content = largeJsonSchemaContent();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        byte[] compressedBody = given().config(NO_AUTO_DECODE).header("Accept-Encoding", "gzip")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Encoding", equalTo("gzip")).extract().asByteArray();

        byte[] uncompressedBody = content.getBytes(StandardCharsets.UTF_8);
        byte[] decompressedBody = gunzip(compressedBody);

        assertEquals(new String(uncompressedBody, StandardCharsets.UTF_8),
                new String(decompressedBody, StandardCharsets.UTF_8));
        assertTrue(compressedBody.length < uncompressedBody.length, "compressed response ("
                + compressedBody.length + " bytes) should be smaller than the uncompressed content ("
                + uncompressedBody.length + " bytes)");
    }

    @Test
    public void testGzippedRequestBodyIsDecompressedByServer() throws Exception {
        String artifactId = "testGzippedRequestBodyIsDecompressedByServer";
        String content = largeJsonSchemaContent();

        CreateArtifact createArtifact = CreateArtifact.builder().artifactId(artifactId)
                .artifactType(ArtifactType.JSON)
                .firstVersion(CreateVersion.builder().content(VersionContent.builder().content(content)
                        .contentType(ContentTypes.APPLICATION_JSON).build()).build())
                .build();

        byte[] requestJson = new ObjectMapper().writeValueAsBytes(createArtifact);
        byte[] gzippedRequestJson = gzip(requestJson);
        assertTrue(gzippedRequestJson.length < requestJson.length, "the request body used by this test "
                + "must actually shrink when gzipped, otherwise it isn't exercising decompression");

        given().config(NO_AUTO_DECODE).contentType(CT_JSON).header("Content-Encoding", "gzip")
                .pathParam("groupId", GROUP).body(gzippedRequestJson)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200)
                .body("artifact.artifactId", equalTo(artifactId));

        given().pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).body("properties.field0.type", equalTo("string"))
                .body("properties.field199.type", equalTo("string"));
    }

    /**
     * Verifies that a response with a content type NOT in the quarkus.http.compress-media-types
     * list is not compressed, even when the client advertises gzip support. The admin export
     * endpoint returns application/zip, which the list deliberately excludes.
     */
    @Test
    public void testResponseNotCompressedForNonListedContentType() {
        // The admin export endpoint returns application/zip, which is not in compress-media-types.
        // Even with Accept-Encoding: gzip, the response must NOT be compressed.
        byte[] rawBody = given().config(NO_AUTO_DECODE).header("Accept-Encoding", "gzip")
                .get("/registry/v3/admin/export")
                .then().statusCode(200).header("Content-Encoding", nullValue()).extract().asByteArray();

        // The response should be a valid ZIP file (starts with PK magic bytes 0x50 0x4B)
        assertTrue(rawBody.length >= 4, "export response should not be empty");
        assertEquals((byte) 0x50, rawBody[0], "response should start with ZIP magic byte 'P'");
        assertEquals((byte) 0x4B, rawBody[1], "response should start with ZIP magic byte 'K'");
    }

    /**
     * Verifies that gzip;q=0 (an explicit client refusal of gzip per RFC 9110 §12.5.3) is
     * correctly treated as a refusal, not as acceptance.
     */
    @Test
    public void testResponseNotCompressedWhenClientRefusesGzip() throws Exception {
        String artifactId = "testResponseNotCompressedWhenClientRefusesGzip";
        String content = largeJsonSchemaContent();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        byte[] rawBody = given().config(NO_AUTO_DECODE).header("Accept-Encoding", "gzip;q=0")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Encoding", nullValue()).extract().asByteArray();

        // The response should be the uncompressed content
        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), rawBody,
                "response body with gzip;q=0 should be uncompressed");
    }

    /**
     * Baseline test: no Accept-Encoding header means no compression.
     */
    @Test
    public void testResponseNotCompressedWithoutAcceptEncodingHeader() throws Exception {
        String artifactId = "testResponseNotCompressedWithoutAcceptEncoding";
        String content = largeJsonSchemaContent();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        byte[] rawBody = given().config(NO_AUTO_DECODE)
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Encoding", nullValue()).extract().asByteArray();

        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), rawBody,
                "response body without Accept-Encoding should be uncompressed");
    }
}

