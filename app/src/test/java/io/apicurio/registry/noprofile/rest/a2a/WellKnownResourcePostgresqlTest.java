package io.apicurio.registry.noprofile.rest.a2a;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the A2A well-known discovery tests against PostgreSQL rather than H2. The structured content search
 * added for agent discovery filters relies on a new table, an escaped LIKE pattern and EXISTS/NOT EXISTS
 * subqueries, none of which are dialect neutral by inspection alone, so the suite is repeated here.
 */
@QuarkusTest
@TestProfile(ExperimentalFeaturesPostgresqlProfile.class)
public class WellKnownResourcePostgresqlTest extends WellKnownResourceTest {
}
