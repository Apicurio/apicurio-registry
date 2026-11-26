package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.agents.AgentFeaturesMysqlProfile;
import io.apicurio.registry.utils.tests.KafkasqlTestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profiles for Agent Card signature verification, with the trusted key set served by
 * {@link JwksStub} on a loopback port.
 */
public final class SignatureVerificationProfiles {

    static final String VERIFY_JWKS = "/verify/jwks";
    public static final String REQUIRE_JWKS = "/require/jwks";
    static final String OFF_JWKS = "/off/jwks";

    private SignatureVerificationProfiles() {
    }

    public static Map<String, String> overrides(String mode, String jwksPath) {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.features.experimental.enabled", "true");
        props.put("apicurio.a2a.enabled", "true");
        props.put("apicurio.a2a.signature-verification.mode", mode);
        props.put("apicurio.a2a.signature-verification.jwks-urls", JwksStub.url(jwksPath));
        // Only 127.0.0.1: a jku naming "localhost", the same server, must not be followed.
        props.put("apicurio.a2a.signature-verification.jku-allowlist", "127.0.0.1");
        props.put("apicurio.a2a.signature-verification.cache-ttl.seconds", "2");
        props.put("apicurio.a2a.signature-verification.fetch.timeout.seconds", "2");
        props.put("apicurio.outbound.insecure-http.enabled", "true");
        props.put("apicurio.outbound.loopback.enabled", "true");
        return props;
    }

    /** {@code verify-if-present}, on the default storage. */
    public static class VerifyIfPresent implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return overrides("verify-if-present", VERIFY_JWKS);
        }
    }

    /** {@code verify-if-present}, on KafkaSQL. */
    public static class VerifyIfPresentKafkaSql extends KafkasqlTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> props = new HashMap<>(super.getConfigOverrides());
            props.putAll(overrides("verify-if-present", VERIFY_JWKS));
            return props;
        }
    }

    /** {@code verify-if-present}, on MySQL. */
    public static class VerifyIfPresentMysql extends AgentFeaturesMysqlProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            Map<String, String> props = new HashMap<>(super.getConfigOverrides());
            props.putAll(overrides("verify-if-present", VERIFY_JWKS));
            return props;
        }
    }

    /** {@code require}, on the default storage. */
    public static class Require implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return overrides("require", REQUIRE_JWKS);
        }
    }

    /** {@code off}, with a key set configured, which writes must never fetch. */
    public static class Off implements QuarkusTestProfile {

        @Override
        public Map<String, String> getConfigOverrides() {
            return overrides("off", OFF_JWKS);
        }
    }
}
