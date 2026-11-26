package io.apicurio.registry.agents.a2a.signature;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The same signature verification tests on KafkaSQL storage.
 */
@QuarkusTest
@TestProfile(SignatureVerificationProfiles.VerifyIfPresentKafkaSql.class)
public class AgentCardSignatureVerificationKafkaSqlTest extends AgentCardSignatureVerificationTest {
}
