package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.CreateArtifact;
import io.apicurio.registry.rest.v3.beans.CreateArtifactResponse;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.dto.DownloadContextType;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class DownloadsResourceTest extends AbstractResourceTestBase {

    private static final String GROUP = "DownloadsResourceTest";

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void testDownloadByContentId() throws Exception {
        String title = "Test Download By Content ID API";
        String artifactContent = resourceToString("openapi-empty.json").replaceAll("Empty API", title);

        String artifactId = "testDownloadByContentId/Empty";

        CreateArtifact createArtifact = TestUtils.serverCreateArtifact(artifactId, ArtifactType.OPENAPI,
                artifactContent, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse createArtifactResponse = given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200).extract()
                .as(CreateArtifactResponse.class);

        long contentId = createArtifactResponse.getVersion().getContentId();

        DownloadContextDto context = DownloadContextDto.builder()
                .type(DownloadContextType.CONTENT_BY_CONTENT_ID)
                .contentId(contentId)
                .expires(System.currentTimeMillis() + 60000)
                .build();
        String downloadId = storage.createDownload(context);

        String content = given().when().pathParam("downloadId", downloadId)
                .get("/registry/v3/downloads/{downloadId}").then().statusCode(200)
                .header("Content-Disposition", equalTo("attachment; filename=\"" + contentId + ".json\""))
                .extract().body().asString();

        Assertions.assertTrue(content.contains("\"openapi\": \"3.0.2\""));
        Assertions.assertTrue(content.contains(title));

        // Attempting to consume the single-use download link again should return 404
        given().when().pathParam("downloadId", downloadId)
                .get("/registry/v3/downloads/{downloadId}").then().statusCode(404);
    }

    @Test
    public void testDownloadByGlobalId() throws Exception {
        String title = "Test Download By Global ID API";
        String artifactContent = resourceToString("openapi-empty.json").replaceAll("Empty API", title);

        String artifactId = "testDownloadByGlobalId/Empty";

        CreateArtifact createArtifact = TestUtils.serverCreateArtifact(artifactId, ArtifactType.OPENAPI,
                artifactContent, ContentTypes.APPLICATION_JSON);
        CreateArtifactResponse createArtifactResponse = given().when().contentType(CT_JSON)
                .pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200).extract()
                .as(CreateArtifactResponse.class);

        long globalId = createArtifactResponse.getVersion().getGlobalId();

        DownloadContextDto context = DownloadContextDto.builder()
                .type(DownloadContextType.CONTENT_BY_GLOBAL_ID)
                .globalId(globalId)
                .expires(System.currentTimeMillis() + 60000)
                .build();
        String downloadId = storage.createDownload(context);

        String content = given().when().pathParam("downloadId", downloadId)
                .get("/registry/v3/downloads/{downloadId}").then().statusCode(200)
                .header("Content-Disposition", equalTo("attachment; filename=\"" + globalId + ".json\""))
                .extract().body().asString();

        Assertions.assertTrue(content.contains("\"openapi\": \"3.0.2\""));
        Assertions.assertTrue(content.contains(title));
    }

    @Test
    public void testDownloadByContentHash() throws Exception {
        String title = "Test Download By Content Hash API";
        String artifactContent = resourceToString("openapi-empty.json").replaceAll("Empty API", title);

        String contentHash = DigestUtils.sha256Hex(artifactContent);
        String artifactId = "testDownloadByContentHash/Empty";

        CreateArtifact createArtifact = TestUtils.serverCreateArtifact(artifactId, ArtifactType.OPENAPI,
                artifactContent, ContentTypes.APPLICATION_JSON);
        given().when().contentType(CT_JSON).pathParam("groupId", GROUP).body(createArtifact)
                .post("/registry/v3/groups/{groupId}/artifacts").then().statusCode(200);

        DownloadContextDto context = DownloadContextDto.builder()
                .type(DownloadContextType.CONTENT_BY_CONTENT_HASH)
                .contentHash(contentHash)
                .expires(System.currentTimeMillis() + 60000)
                .build();
        String downloadId = storage.createDownload(context);

        String content = given().when().pathParam("downloadId", downloadId)
                .get("/registry/v3/downloads/{downloadId}").then().statusCode(200)
                .header("Content-Disposition", equalTo("attachment; filename=\"" + contentHash + ".json\""))
                .extract().body().asString();

        Assertions.assertTrue(content.contains("\"openapi\": \"3.0.2\""));
        Assertions.assertTrue(content.contains(title));
    }
}
