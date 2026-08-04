package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.config.DecoderConfig;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Verifies that the runtime kill switch {@code apicurio.rest.compression.enabled=false}
 * disables the custom {@link io.apicurio.registry.rest.HttpCompressionWriterInterceptor},
 * resulting in uncompressed responses even when the client advertises gzip support.
 */
@QuarkusTest
@TestProfile(HttpCompressionDisabledTest.CompressionDisabledProfile.class)
public class HttpCompressionDisabledTest extends AbstractResourceTestBase {

    private static final String GROUP = "HttpCompressionDisabledTest";

    private static final RestAssuredConfig NO_AUTO_DECODE = RestAssuredConfig.newConfig()
            .decoderConfig(DecoderConfig.decoderConfig().noContentDecoders());

    /**
     * Repeated content that would normally be compressed.
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

    @Test
    public void testResponseNotCompressedWhenKillSwitchDisabled() throws Exception {
        String artifactId = "testResponseNotCompressedWhenKillSwitchDisabled";
        String content = largeJsonSchemaContent();
        createArtifact(GROUP, artifactId, ArtifactType.JSON, content, ContentTypes.APPLICATION_JSON);

        // Even though the client advertises gzip, the kill switch should prevent compression
        byte[] rawBody = given().config(NO_AUTO_DECODE).header("Accept-Encoding", "gzip")
                .pathParam("groupId", GROUP).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/branch=latest/content")
                .then().statusCode(200).header("Content-Encoding", nullValue()).extract().asByteArray();

        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), rawBody,
                "response should be uncompressed when apicurio.rest.compression.enabled=false");
    }

    public static class CompressionDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("apicurio.rest.compression.enabled", "false");
        }
    }
}
