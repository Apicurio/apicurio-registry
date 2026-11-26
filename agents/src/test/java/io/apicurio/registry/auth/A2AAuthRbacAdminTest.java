package io.apicurio.registry.auth;

import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Tag;

/**
 * Runs the {@link A2AAuthTest} visibility tests with admin override off. An RBAC administrator must
 * still see private Agent Cards, as with every other authorization check (issue #10951).
 */
@QuarkusTest
@TestProfile(A2AAuthRbacAdminTestProfile.class)
@Tag(ApicurioTestTags.SLOW)
public class A2AAuthRbacAdminTest extends A2AAuthTest {
}
