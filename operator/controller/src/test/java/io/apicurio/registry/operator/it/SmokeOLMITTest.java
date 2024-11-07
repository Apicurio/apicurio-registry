package io.apicurio.registry.operator.it;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;

import static io.apicurio.registry.operator.it.SmokeITTest.smoke;

@QuarkusTest
@DisabledIf("io.apicurio.registry.operator.it.OLMITBase#disabledIf")
public class SmokeOLMITTest extends OLMITBase {

    @Test
    void runSmoke() {
        smoke(client, namespace, ingressManager);
    }
}
