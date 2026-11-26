package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.PublicJsonWebKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static io.apicurio.registry.agents.a2a.signature.SignatureVerificationProfiles.VERIFY_JWKS;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.artifactStatus;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.card;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.createCard;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.createVersion;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.discovery;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.publishDraft;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.setLabels;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Agent Card signature verification in {@code verify-if-present} mode, end to end: the write policy, the
 * verified signers of discovery results, the {@code signedBy} lookup, {@code jku} handling and key
 * rotation. Subclasses run the same tests on other storages.
 */
@QuarkusTest
@TestProfile(SignatureVerificationProfiles.VerifyIfPresent.class)
public class AgentCardSignatureVerificationTest extends AbstractResourceTestBase {

    /** The keys served at {@link SignatureVerificationProfiles#VERIFY_JWKS}, by kid. */
    private static final Map<String, PublicJsonWebKey> SERVED = new ConcurrentHashMap<>();
    private static PublicJsonWebKey trusted;

    @BeforeAll
    static void serveTrustedKey() {
        if (trusted == null) {
            trusted = TestCardSigner.generateKey("ES256", "trusted-" + UUID.randomUUID());
            SERVED.put(trusted.getKeyId(), trusted);
        }
        serveKeys();
    }

    private static void serveKeys() {
        JwksStub.get().serve(VERIFY_JWKS, TestCardSigner.jwks(SERVED.values().toArray(JsonWebKey[]::new)));
    }

    private static String skill() {
        return "skill-" + UUID.randomUUID();
    }

    private static Response search(String skill, String signedBy) {
        var request = discovery().queryParam("skill", skill);
        if (signedBy != null) {
            request = request.queryParam("signedBy", signedBy);
        }
        Response response = request.get("/.well-known/agents");
        response.then().statusCode(200);
        return response;
    }

    @Test
    void unsignedCardIsAcceptedAndHasNoVerifiedSigner() {
        String group = TestUtils.generateGroupId();
        String skill = skill();

        createCard(group, "unsigned", card(skill), false).then().statusCode(200);

        search(skill, null).then().body("count", equalTo(1)).body("agents[0].verifiedSigners", empty());
    }

    @Test
    void validSignatureIsAcceptedAndReportedAsVerified() {
        String group = TestUtils.generateGroupId();
        String skill = skill();

        createCard(group, "signed", TestCardSigner.sign(card(skill), trusted, "ES256"), false).then().statusCode(200);

        search(skill, null).then().body("count", equalTo(1))
                .body("agents[0].verifiedSigners", contains(trusted.getKeyId()));
        search(skill, trusted.getKeyId()).then().body("count", equalTo(1))
                .body("agents[0].artifactId", equalTo("signed"));
    }

    @Test
    void cardWithOnlyAnInvalidSignatureIsRejectedEvenOnADryRun() {
        String group = TestUtils.generateGroupId();
        String signed = TestCardSigner.sign(card(skill()), trusted, "ES256");
        String tampered = signed.replace("Weather Agent", "Evil Agent");
        String signatureValue = signed.substring(signed.indexOf("\"signature\":\"") + 13).split("\"")[0];

        for (boolean dryRun : new boolean[] { true, false }) {
            Response response = createCard(group, "tampered", tampered, dryRun);

            response.then().statusCode(400)
                    .body("causes.description", contains(
                            "Signature by kid '" + trusted.getKeyId() + "' rejected: invalid signature"))
                    .body("causes.context", contains("/signatures/0"));
            String body = response.asString();
            assertFalse(body.contains(signatureValue), "the signature value must not be echoed");
            assertFalse(body.contains(trusted.toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)),
                    "key material must not be echoed");
            assertFalse(body.contains("\"keys\""), "the key set must not be echoed");
        }
        artifactStatus(group, "tampered").then().statusCode(404);
    }

    @Test
    void newVersionWithAnUnknownKeyIsRejected() {
        String group = TestUtils.generateGroupId();
        String skill = skill();
        PublicJsonWebKey stranger = TestCardSigner.generateKey("EdDSA", "stranger-" + UUID.randomUUID());
        createCard(group, "versioned", card(skill), false).then().statusCode(200);

        createVersion(group, "versioned", TestCardSigner.sign(card(skill).replace("1.0.0", "1.0.1"), stranger,
                "EdDSA")).then().statusCode(400)
                .body("causes.description", contains(
                        "Signature by kid '" + stranger.getKeyId() + "' rejected: unknown key"));
    }

    @Test
    void draftIsCheckedWhenItIsPublished() {
        String group = TestUtils.generateGroupId();
        String tampered = TestCardSigner.sign(card(skill()), trusted, "ES256").replace("Forecasts", "Lies");

        createCard(group, "draft", tampered, false, true).then().statusCode(200);

        publishDraft(group, "draft", "1").then().statusCode(400)
                .body("causes.description", contains(
                        "Signature by kid '" + trusted.getKeyId() + "' rejected: invalid signature"));
    }

    /** One trusted signature and one by an unknown key: accepted, and found only by the trusted kid. */
    @Test
    void cardSignedByATrustedAndAnUnknownKey() {
        String group = TestUtils.generateGroupId();
        String skill = skill();
        PublicJsonWebKey unknown = TestCardSigner.generateKey("ES256", "unknown-" + UUID.randomUUID());
        String twiceSigned = TestCardSigner.sign(TestCardSigner.sign(card(skill), unknown, "ES256"), trusted, "ES256");

        createCard(group, "twice-signed", twiceSigned, false).then().statusCode(200);

        search(skill, null).then().body("agents[0].verifiedSigners", contains(trusted.getKeyId()));
        search(skill, trusted.getKeyId()).then().body("count", equalTo(1))
                .body("agents[0].artifactId", equalTo("twice-signed"));
        search(skill, unknown.getKeyId()).then().body("count", equalTo(0));
    }

    @Test
    void jkuOnAHostOutsideTheAllowListIsNeverFetched() {
        String group = TestUtils.generateGroupId();
        String path = "/verify/jku-denied-" + UUID.randomUUID();
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "jku-denied-" + UUID.randomUUID());
        JwksStub.get().serve(path, TestCardSigner.jwks(key));
        // The same stub, named "localhost", which is not in the allow-list.
        String signed = TestCardSigner.sign(card(skill()), key, "ES256", Map.of("alg", "ES256", "typ", "JOSE",
                "kid", key.getKeyId(), "jku", JwksStub.url("localhost", path)));

        createCard(group, "jku-denied", signed, false).then().statusCode(400)
                .body("causes.description", contains("Signature by kid '" + key.getKeyId() + "' rejected: unknown key"));

        assertEquals(0, JwksStub.get().requestCount(path));
    }

    @Test
    void jkuOnAnAllowListedHostIsFetched() {
        String group = TestUtils.generateGroupId();
        String skill = skill();
        String path = "/verify/jku-allowed-" + UUID.randomUUID();
        PublicJsonWebKey key = TestCardSigner.generateKey("RS256", "jku-allowed-" + UUID.randomUUID());
        JwksStub.get().serve(path, TestCardSigner.jwks(key));
        String signed = TestCardSigner.sign(card(skill), key, "RS256", Map.of("alg", "RS256", "typ", "JOSE",
                "kid", key.getKeyId(), "jku", JwksStub.url("127.0.0.1", path)));

        createCard(group, "jku-allowed", signed, false).then().statusCode(200);

        assertEquals(1, JwksStub.get().requestCount(path));
        search(skill, key.getKeyId()).then().body("count", equalTo(1))
                .body("agents[0].verifiedSigners", contains(key.getKeyId()));
    }

    @Test
    void rotatedKeyVerifiesOnceItIsPublished() {
        String group = TestUtils.generateGroupId();
        String skill = skill();
        PublicJsonWebKey rotated = TestCardSigner.generateKey("PS256", "rotated-" + UUID.randomUUID());
        String signed = TestCardSigner.sign(card(skill), rotated, "PS256");
        createCard(group, "rotated", signed, true).then().statusCode(400);

        SERVED.put(rotated.getKeyId(), rotated);
        serveKeys();

        // An unknown kid refreshes the key set at most once per cache-ttl, so this may take a retry.
        // Dry runs only, so a retry writes nothing.
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> createCard(group, "rotated", signed, true).then().statusCode(200));
        createCard(group, "rotated", signed, false).then().statusCode(200);
        search(skill, rotated.getKeyId()).then().body("count", equalTo(1))
                .body("agents[0].verifiedSigners", contains(rotated.getKeyId()));
    }

    @Test
    void removedKeyStopsBeingReportedWithoutAnyWrite() {
        String group = TestUtils.generateGroupId();
        String skill = skill();
        PublicJsonWebKey removable = TestCardSigner.generateKey("EdDSA", "removable-" + UUID.randomUUID());
        SERVED.put(removable.getKeyId(), removable);
        serveKeys();
        String signed = TestCardSigner.sign(card(skill), removable, "EdDSA");
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> createCard(group, "removable", signed, true).then().statusCode(200));
        createCard(group, "removable", signed, false).then().statusCode(200);
        search(skill, removable.getKeyId()).then().body("count", equalTo(1))
                .body("agents[0].verifiedSigners", contains(removable.getKeyId()));

        SERVED.remove(removable.getKeyId());
        serveKeys();

        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            search(skill, null).then().body("count", equalTo(1)).body("agents[0].verifiedSigners", empty());
            search(skill, removable.getKeyId()).then().body("count", equalTo(0));
        });
    }

    @Test
    void labelsCannotCreateOrRemoveAVerifiedSigner() {
        String group = TestUtils.generateGroupId();
        String unsignedSkill = skill();
        String signedSkill = skill();
        createCard(group, "unsigned", card(unsignedSkill), false).then().statusCode(200);
        createCard(group, "signed", TestCardSigner.sign(card(signedSkill), trusted, "ES256"), false).then()
                .statusCode(200);

        Map<String, String> forged = Map.of("signedBy", trusted.getKeyId(), "verifiedSigners", trusted.getKeyId(),
                "apicurio.a2a.signer", trusted.getKeyId());
        setLabels(group, "unsigned", forged);
        setLabels(group, "signed", Map.of("verifiedSigners", "", "signedBy", "nobody"));

        search(unsignedSkill, null).then().body("agents[0].verifiedSigners", empty());
        search(unsignedSkill, trusted.getKeyId()).then().body("count", equalTo(0));
        search(signedSkill, null).then().body("agents[0].verifiedSigners", contains(trusted.getKeyId()));
        search(signedSkill, trusted.getKeyId()).then().body("count", equalTo(1));
    }

    @Test
    void signedByMustNotBeBlank() {
        discovery().queryParam("signedBy", " ").get("/.well-known/agents").then().statusCode(400)
                .body("detail", containsString("signedBy"));
    }

    @Test
    void verifiedSignersAreAlsoInTheV3WellKnownSearch() {
        String group = TestUtils.generateGroupId();
        String skill = skill();
        createCard(group, "signed", TestCardSigner.sign(card(skill), trusted, "ES256"), false).then().statusCode(200);

        RestAssured.given().queryParam("skill", skill).queryParam("signedBy", trusted.getKeyId())
                .get("/registry/v3/well-known/agents").then().statusCode(200).body("count", equalTo(1))
                .body("agents[0].verifiedSigners", contains(trusted.getKeyId()));
    }
}
