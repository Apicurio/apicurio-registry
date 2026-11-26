package io.apicurio.registry.agents.a2a.signature;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCardSignatureConfigTest {

    private static AgentCardSignatureConfig config(String mode, List<String> algs) {
        AgentCardSignatureConfig config = new AgentCardSignatureConfig();
        config.modeValue = mode;
        config.allowedAlgs = algs;
        config.jwksUrls = Optional.empty();
        config.jkuAllowedHosts = Optional.empty();
        return config;
    }

    @Test
    void defaultsAreOffWithNoKeySource() {
        AgentCardSignatureConfig config = config("off", List.of("ES256", "EdDSA", "RS256", "PS256"));

        config.validate();

        assertEquals(AgentCardSignatureConfig.Mode.OFF, config.getMode());
        assertEquals(Set.of("ES256", "EdDSA", "RS256", "PS256"), config.getAllowedAlgorithms());
        assertFalse(config.isVerificationConfigured());
    }

    @ParameterizedTest
    @ValueSource(strings = { "none", "HS256", "es256" })
    void refusesAlgorithmsThatAreNotAsymmetricJwsAlgorithms(String alg) {
        AgentCardSignatureConfig config = config("off", List.of("ES256", alg));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, config::validate);

        assertTrue(error.getMessage().contains("'" + alg + "'"));
    }

    @Test
    void refusesAnUnknownMode() {
        AgentCardSignatureConfig config = config("strict", List.of("ES256"));

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void parsesModesCaseInsensitively() {
        AgentCardSignatureConfig config = config(" Verify-If-Present ", List.of("ES256"));
        config.jwksUrls = Optional.of(List.of(" https://keys.example.com/jwks.json ", ""));

        config.validate();

        assertEquals(AgentCardSignatureConfig.Mode.VERIFY_IF_PRESENT, config.getMode());
        assertEquals(List.of("https://keys.example.com/jwks.json"), config.getJwksUrls());
        assertTrue(config.isVerificationConfigured());
    }
}
