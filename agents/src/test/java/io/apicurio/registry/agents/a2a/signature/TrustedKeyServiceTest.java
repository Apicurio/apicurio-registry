package io.apicurio.registry.agents.a2a.signature;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrustedKeyServiceTest {

    @Test
    void jkuAllowListMatchesExactHostsAndSubdomainWildcards() {
        AgentCardSignatureConfig config = new AgentCardSignatureConfig();
        config.jkuAllowlist = Optional.of(List.of("keys.example.com", "*.trusted.example"));
        TrustedKeyService service = new TrustedKeyService();
        service.config = config;

        assertTrue(service.isAllowedJku("https://keys.example.com/jwks.json"));
        assertTrue(service.isAllowedJku("https://KEYS.example.com./jwks.json"));
        assertTrue(service.isAllowedJku("https://a.trusted.example/jwks.json"));
        assertFalse(service.isAllowedJku("https://trusted.example/jwks.json"));
        assertFalse(service.isAllowedJku("https://keys.example.com.evil.example/jwks.json"));
        assertFalse(service.isAllowedJku("https://evilkeys.example.com/jwks.json"));
        assertFalse(service.isAllowedJku("not a url"));
    }
}
