package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.PublicJsonWebKey;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.apicurio.registry.agents.a2a.signature.SignatureVerificationProfiles.OFF_JWKS;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.card;
import static io.apicurio.registry.agents.a2a.signature.SignedCardRequests.createCard;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code off} mode: writes check nothing and fetch nothing, even with a key set configured.
 */
@QuarkusTest
@TestProfile(SignatureVerificationProfiles.Off.class)
public class AgentCardSignatureOffModeTest extends AbstractResourceTestBase {

    @Test
    void writesNeverCheckSignaturesNorFetchKeys() {
        String group = TestUtils.generateGroupId();
        PublicJsonWebKey key = TestCardSigner.generateKey("ES256", "off-" + UUID.randomUUID());
        JwksStub.get().serve(OFF_JWKS, TestCardSigner.jwks(key));
        String signed = TestCardSigner.sign(card("skill-" + UUID.randomUUID()), key, "ES256");
        PublicJsonWebKey stranger = TestCardSigner.generateKey("ES256", "stranger-" + UUID.randomUUID());

        createCard(group, "unsigned", card("skill-" + UUID.randomUUID()), false).then().statusCode(200);
        createCard(group, "signed", signed, false).then().statusCode(200);
        createCard(group, "tampered", signed.replace("Weather Agent", "Evil Agent"), false).then().statusCode(200);
        createCard(group, "unknown-key", TestCardSigner.sign(card("skill-" + UUID.randomUUID()), stranger, "ES256"),
                false).then().statusCode(200);

        assertEquals(0, JwksStub.get().requestCount(OFF_JWKS));
    }
}
