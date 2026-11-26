package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
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
}
