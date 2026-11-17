package io.apicurio.registry.noprofile.rest.v3;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;

/**
 * The {@code references} parameter on {@code GET /ids/contentIds/{contentId}} and
 * {@code GET /ids/contentHashes/{contentHash}} (#11134).
 */
@QuarkusTest
public class IdsContentReferencesTest extends AbstractIdsContentReferencesTest {

    @Test
    public void withoutParameterContentIsReturnedAsStored() {
        for (String path : List.of(byId(), byHash())) {
            String body = get().get(path).then().statusCode(200).extract().asString();
            Assertions.assertEquals(withRefContent, body, path);
            String preserved = get().queryParam("references", "PRESERVE").get(path).then().statusCode(200)
                    .extract().asString();
            Assertions.assertEquals(withRefContent, preserved, path);
        }
    }

    @Test
    public void dereferenceInlinesReferencedContent() {
        for (String path : List.of(byId(), byHash())) {
            get().queryParam("references", "DEREFERENCE").get(path).then().statusCode(200)
                    .body("openapi", equalTo("3.0.2"))
                    .body(WIDGET_REF, equalTo("#/components/schemas/Widget"))
                    .body("components.schemas.Widget.title", equalTo("Root Type for Widget"))
                    .body("components.schemas.Widget.properties.name.type", equalTo("string"));
        }
    }

    @Test
    public void rewritePointsReferencesAtRegistryUrls() {
        for (String path : List.of(byId(), byHash())) {
            get().queryParam("references", "REWRITE").get(path).then().statusCode(200)
                    .body(WIDGET_REF, endsWith("/apis/registry/v3/groups/" + group
                            + "/artifacts/ReferencedTypes/versions/1/content?references=REWRITE"
                            + "#/components/schemas/Widget"));
        }
    }

    @Test
    public void unknownIdsAreNotFound() {
        get().queryParam("references", "DEREFERENCE").get("/registry/v3/ids/contentIds/" + Integer.MAX_VALUE)
                .then().statusCode(404);
        get().queryParam("references", "DEREFERENCE").get("/registry/v3/ids/contentHashes/not-a-hash")
                .then().statusCode(404);
    }
}
