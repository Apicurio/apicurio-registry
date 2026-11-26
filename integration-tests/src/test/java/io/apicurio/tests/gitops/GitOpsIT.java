package io.apicurio.tests.gitops;

import io.apicurio.tests.ReadOnlyRegistryBaseIT;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;

import static io.apicurio.deployment.Constants.GITOPS;

@Tag(GITOPS)
@QuarkusIntegrationTest
@Isolated
public class GitOpsIT extends ReadOnlyRegistryBaseIT {
}
