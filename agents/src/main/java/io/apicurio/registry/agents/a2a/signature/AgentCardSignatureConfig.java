package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.common.apps.config.Info;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_A2A;

/**
 * Configuration of the verification of signed A2A Agent Cards (A2A v1.0, section 8.4).
 */
@Singleton
public class AgentCardSignatureConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentCardSignatureConfig.class);

    /** The asymmetric JWS algorithms that may be allowed. {@code none} and HMAC algorithms never are. */
    static final Set<String> SUPPORTED_ALGORITHMS = Set.of("ES256", "ES384", "ES512", "EdDSA", "RS256", "RS384",
            "RS512", "PS256", "PS384", "PS512");

    /**
     * What a write of an {@code AGENT_CARD} version requires of its signatures.
     */
    public enum Mode {
        /** Signatures are not checked on write. */
        OFF("off"),
        /** An unsigned card is accepted; a signed card needs at least one signature that verifies. */
        VERIFY_IF_PRESENT("verify-if-present"),
        /** Every card needs at least one signature that verifies. */
        REQUIRE("require");

        private final String value;

        Mode(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        static Mode parse(String value) {
            for (Mode mode : values()) {
                if (mode.value.equals(value.trim().toLowerCase(Locale.ROOT))) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Invalid value for apicurio.a2a.signature-verification.mode: '"
                    + value + "'. Must be one of: off, verify-if-present, require");
        }
    }

    @ConfigProperty(name = "apicurio.a2a.signature-verification.mode", defaultValue = "off")
    @Info(category = CATEGORY_A2A, description = "Signature policy applied to every AGENT_CARD version published through the REST API, whether or not rules are enabled: 'off' (no check), 'verify-if-present' (an unsigned card is accepted, a signed card needs at least one signature that verifies) or 'require' (every card needs at least one signature that verifies)", availableSince = "3.4.0", experimental = true)
    String modeValue;

    @ConfigProperty(name = "apicurio.a2a.signature-verification.allowed-algs", defaultValue = "ES256,EdDSA,RS256,PS256")
    @Info(category = CATEGORY_A2A, description = "JWS algorithms accepted in Agent Card signatures. Only asymmetric algorithms (ES256, ES384, ES512, EdDSA, RS256, RS384, RS512, PS256, PS384, PS512) can be listed; 'none' is never accepted", availableSince = "3.4.0")
    List<String> allowedAlgs;

    @ConfigProperty(name = "apicurio.a2a.signature-verification.jwks-urls")
    @Info(category = CATEGORY_A2A, description = "URLs of the JSON Web Key Sets holding the public keys of trusted Agent Card publishers. Agent Card signatures are verified whenever this or apicurio.a2a.signature-verification.jku-allowlist is set, including in discovery results, whatever the mode. The URLs must use HTTPS unless apicurio.outbound.insecure-http.enabled is set", availableSince = "3.4.0")
    Optional<List<String>> jwksUrls;

    @ConfigProperty(name = "apicurio.a2a.signature-verification.jku-allowlist")
    @Info(category = CATEGORY_A2A, description = "Hosts whose JSON Web Key Sets may be fetched from the 'jku' header of an Agent Card signature, as exact host names or '*.domain' wildcards. A 'jku' naming any other host is never fetched. Empty (the default) means 'jku' is never followed", availableSince = "3.4.0")
    Optional<List<String>> jkuAllowlist;

    @ConfigProperty(name = "apicurio.a2a.signature-verification.fetch.timeout.seconds", defaultValue = "5")
    @Info(category = CATEGORY_A2A, description = "Timeout, in seconds, for connecting to a JSON Web Key Set URL and again for receiving its response headers", availableSince = "3.4.0")
    int fetchTimeoutSeconds;

    @ConfigProperty(name = "apicurio.a2a.signature-verification.fetch.max-size.bytes", defaultValue = "65536")
    @Info(category = CATEGORY_A2A, description = "Largest JSON Web Key Set accepted, in bytes; a larger response is refused", availableSince = "3.4.0")
    int fetchMaxSizeBytes;

    @ConfigProperty(name = "apicurio.a2a.signature-verification.cache-ttl.seconds", defaultValue = "300")
    @Info(category = CATEGORY_A2A, description = "How long a fetched JSON Web Key Set is used before it is fetched again, in seconds. A signature naming an unknown 'kid' triggers at most one early refresh per key set in this period, so rotated keys are picked up", availableSince = "3.4.0")
    int cacheTtlSeconds;

    @ConfigProperty(name = "apicurio.a2a.signature-verification.cache.max-size", defaultValue = "1000")
    @Info(category = CATEGORY_A2A, description = "Number of Agent Card contents whose verified signers are cached for discovery results", availableSince = "3.4.0")
    int cacheMaxSize;

    private Mode mode;
    private Set<String> allowedAlgorithms;

    @PostConstruct
    void validate() {
        mode = Mode.parse(modeValue);
        allowedAlgorithms = new LinkedHashSet<>();
        for (String alg : allowedAlgs) {
            String trimmed = alg.trim();
            if (!SUPPORTED_ALGORITHMS.contains(trimmed)) {
                throw new IllegalArgumentException(
                        "Invalid value for apicurio.a2a.signature-verification.allowed-algs: '" + trimmed
                                + "'. Must be among: " + SUPPORTED_ALGORITHMS);
            }
            allowedAlgorithms.add(trimmed);
        }
        if (mode != Mode.OFF && !isVerificationConfigured()) {
            log.warn("apicurio.a2a.signature-verification.mode is '{}' but no trusted keys are configured "
                    + "(apicurio.a2a.signature-verification.jwks-urls and jku-allowlist are empty): no "
                    + "Agent Card signature can verify.", mode.getValue());
        }
    }

    public Mode getMode() {
        return mode;
    }

    public Set<String> getAllowedAlgorithms() {
        return allowedAlgorithms;
    }

    public List<String> getJwksUrls() {
        return jwksUrls.orElse(List.of()).stream().map(String::trim).filter(url -> !url.isEmpty()).toList();
    }

    public List<String> getJkuAllowlist() {
        return jkuAllowlist.orElse(List.of()).stream().map(String::trim).filter(host -> !host.isEmpty()).toList();
    }

    /**
     * @return whether any key source is configured; when not, nothing is ever fetched
     */
    public boolean isVerificationConfigured() {
        return !getJwksUrls().isEmpty() || !getJkuAllowlist().isEmpty();
    }

    public int getFetchTimeoutSeconds() {
        return fetchTimeoutSeconds;
    }

    public int getFetchMaxSizeBytes() {
        return fetchMaxSizeBytes;
    }

    public int getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }
}
