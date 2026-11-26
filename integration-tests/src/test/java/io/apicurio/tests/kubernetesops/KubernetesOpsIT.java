package io.apicurio.tests.kubernetesops;

import io.apicurio.tests.ReadOnlyRegistryBaseIT;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;

import static io.apicurio.deployment.Constants.KUBERNETES_OPS;

@Tag(KUBERNETES_OPS)
@QuarkusIntegrationTest
@Isolated
public class KubernetesOpsIT extends ReadOnlyRegistryBaseIT {
}
