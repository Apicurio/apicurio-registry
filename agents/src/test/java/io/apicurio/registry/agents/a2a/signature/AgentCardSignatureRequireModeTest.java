package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.apicurio.registry.agents.a2a.signature.SignatureVerificationProfiles.REQUIRE_JWKS;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.artifactStatus;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.card;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.createCard;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.createVersion;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@code require} mode: every published Agent Card needs a signature that verifies, whether or not a
 * rule is enabled (none is, here).
 */
@QuarkusTest
@TestProfile(SignatureVerificationProfiles.Require.class)
public class AgentCardSignatureRequireModeTest extends AbstractResourceTestBase {

    private static final PublicJsonWebKey TRUSTED = TestCardSigner.generateKey("EdDSA", "require-" + UUID.randomUUID());

    @BeforeAll
    static void serveTrustedKey() {
        JwksStub.get().serve(REQUIRE_JWKS, TestCardSigner.jwks(TRUSTED));
    }

    @Test
    void unsignedCardIsRejectedEvenOnADryRun() {
        String group = TestUtils.generateGroupId();

        for (boolean dryRun : new boolean[] { true, false }) {
            createCard(group, "unsigned", card("skill-" + UUID.randomUUID()), dryRun).then().statusCode(400)
                    .body("causes.description", contains("The Agent Card is not signed: no verifiable signature"))
                    .body("causes.context", contains("/signatures"));
        }
        artifactStatus(group, "unsigned").then().statusCode(404);
    }

    @Test
    void signedCardIsAcceptedAndAnUnsignedNewVersionIsNot() {
        String group = TestUtils.generateGroupId();
        String skill = "skill-" + UUID.randomUUID();

        createCard(group, "signed", TestCardSigner.sign(card(skill), TRUSTED, "EdDSA"), false).then().statusCode(200);

        createVersion(group, "signed", card(skill).replace("1.0.0", "1.0.1")).then().statusCode(400)
                .body("causes.description", contains("The Agent Card is not signed: no verifiable signature"));
    }

    @Test
    void v2CreateOfAnUnsignedCardIsRejected() {
        String group = TestUtils.generateGroupId();

        v2Create(group, "v2-unsigned", card("skill-" + UUID.randomUUID())).then().statusCode(400)
                .body("message", equalTo(AgentCardSignatureWriteHook.REJECTED))
                .body("causes.description", contains("The Agent Card is not signed: no verifiable signature"));

        artifactStatus(group, "v2-unsigned").then().statusCode(404);
    }

    @Test
    void v2NewVersionAndUpdateOfAnUnsignedCardAreRejected() {
        String group = TestUtils.generateGroupId();
        String skill = "skill-" + UUID.randomUUID();
        v2Create(group, "v2-signed", TestCardSigner.sign(card(skill), TRUSTED, "EdDSA")).then().statusCode(200);
        String unsigned = card(skill).replace("1.0.0", "1.0.1");

        RestAssured.given().contentType(ContentType.JSON).body(unsigned)
                .post("/registry/v2/groups/{g}/artifacts/{a}/versions", group, "v2-signed").then().statusCode(400)
                .body("causes.description", contains("The Agent Card is not signed: no verifiable signature"));
        RestAssured.given().contentType(ContentType.JSON).body(unsigned)
                .put("/registry/v2/groups/{g}/artifacts/{a}", group, "v2-signed").then().statusCode(400)
                .body("causes.description", contains("The Agent Card is not signed: no verifiable signature"));
        RestAssured.given().contentType(ContentType.JSON).body(unsigned)
                .put("/registry/v2/groups/{g}/artifacts/{a}/test", group, "v2-signed").then().statusCode(400);

        RestAssured.given().get("/registry/v3/groups/{g}/artifacts/{a}/versions", group, "v2-signed").then()
                .statusCode(200).body("count", equalTo(1));
    }

    @Test
    void v2CreateOfAValidSignedCardIsAcceptedAndABadOneLeaksNoKeyMaterial() {
        String group = TestUtils.generateGroupId();
        String signed = TestCardSigner.sign(card("skill-" + UUID.randomUUID()), TRUSTED, "EdDSA");
        v2Create(group, "v2-valid", signed).then().statusCode(200).body("type", equalTo("AGENT_CARD"));

        String tampered = signed.replace("Weather Agent", "Evil Agent");
        Response response = v2Create(group, "v2-tampered", tampered);

        response.then().statusCode(400).body("causes.description",
                contains("Signature by kid '" + TRUSTED.getKeyId() + "' rejected: invalid signature"));
        String signatureValue = signed.substring(signed.indexOf("\"signature\":\"") + 13).split("\"")[0];
        assertFalse(response.asString().contains(signatureValue));
        assertFalse(response.asString().contains(TRUSTED.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)));
        assertFalse(response.asString().contains("\"keys\""));
    }

    /** The Confluent-compatible API infers AGENT_CARD from the content, so the policy applies there too. */
    @Test
    void ccompatRegistrationOfAnUnsignedCardIsRejected() throws Exception {
        String subject = "ccompat-" + UUID.randomUUID();
        String body = new ObjectMapper().createObjectNode().put("schema", card("skill-" + UUID.randomUUID()))
                .toString();

        RestAssured.given().contentType(ContentType.JSON).body(body)
                .post("/ccompat/v7/subjects/{s}/versions", subject).then().statusCode(422)
                .body("message", equalTo(AgentCardSignatureWriteHook.REJECTED));

        artifactStatus("default", subject).then().statusCode(404);
    }

    private static Response v2Create(String group, String artifactId, String content) {
        return RestAssured.given().contentType(ContentType.JSON).header("X-Registry-ArtifactType", "AGENT_CARD")
                .header("X-Registry-ArtifactId", artifactId).body(content)
                .post("/registry/v2/groups/{g}/artifacts", group);
    }
}
