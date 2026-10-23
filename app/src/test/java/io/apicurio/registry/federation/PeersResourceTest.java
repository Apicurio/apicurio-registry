package io.apicurio.registry.federation;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(FederationEnabledProfile.class)
public class PeersResourceTest extends AbstractPeersResourceTest {
}
