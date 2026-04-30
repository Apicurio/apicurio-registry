package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.noprofile.auth.SearchFilteringBenchmarkTest;
import io.apicurio.registry.storage.util.PostgresqlTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Runs the search filtering benchmark against embedded PostgreSQL
 * instead of H2 to measure realistic overhead.
 *
 * Enable with: -DSearchFilteringBenchmarkTest=enabled
 */
@QuarkusTest
@TestProfile(PostgresqlTestProfile.class)
public class SearchFilteringPostgresqlBenchmarkTest extends SearchFilteringBenchmarkTest {
}
