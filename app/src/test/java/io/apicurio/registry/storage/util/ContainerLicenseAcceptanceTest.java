package io.apicurio.registry.storage.util;

import io.apicurio.registry.utils.tests.MsSqlEmbeddedTestResource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Testcontainers compares the license acceptance file against the image name as an exact string, tag
 * included. MsSqlEmbeddedTestResource calls acceptLicense(), which bypasses that check on every current
 * run, so a drift between the two goes unnoticed until someone drops that call. This test fails at the
 * drift instead.
 */
class ContainerLicenseAcceptanceTest {

    @Test
    void testAcceptsTheSqlServerImageTheTestsStart() throws IOException {
        List<String> accepted;
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("container-license-acceptance.txt")) {
            Assertions.assertNotNull(in, "container-license-acceptance.txt must be on the test classpath");
            accepted = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    accepted.add(line.trim());
                }
            }
        }

        Assertions.assertTrue(accepted.contains(MsSqlEmbeddedTestResource.IMAGE.asCanonicalNameString()),
                "container-license-acceptance.txt must list " + MsSqlEmbeddedTestResource.IMAGE.asCanonicalNameString());
    }
}
