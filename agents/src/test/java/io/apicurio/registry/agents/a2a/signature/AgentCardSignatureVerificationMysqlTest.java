package io.apicurio.registry.agents.a2a.signature;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * The same signature verification tests on MySQL storage.
 */
@QuarkusTest
@TestProfile(SignatureVerificationProfiles.VerifyIfPresentMysql.class)
public class AgentCardSignatureVerificationMysqlTest extends AgentCardSignatureVerificationTest {
}
