package io.apicurio.registry.agents.a2a.signature;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies Agent Card signatures against the trusted keys of {@link TrustedKeyService}.
 * <p>
 * Nothing is stored: which keys signed a card is computed when it is read, from its content and the
 * current keys, so labels and metadata cannot create or remove a verified signer, and a key removed
 * from the trusted key sets stops counting once the key set is fetched again. Results for discovery are
 * cached by content digest and key epoch, in a bounded cache.
 * </p>
 * <p>
 * When no key source is configured, nothing is verified and nothing is fetched.
 * </p>
 */
@ApplicationScoped
public class AgentCardSignatureService {

    @Inject
    AgentCardSignatureConfig config;

    @Inject
    TrustedKeyService trustedKeys;

    private Map<String, List<String>> verifiedSignersCache;

    @PostConstruct
    void init() {
        int maxSize = Math.max(1, config.getCacheMaxSize());
        verifiedSignersCache = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
                return size() > maxSize;
            }
        };
    }

    public boolean isVerificationConfigured() {
        return config.isVerificationConfigured();
    }

    /**
     * Verifies every signature of the card, without caching.
     */
    public AgentCardSignatureVerifier.Result verify(String cardJson) {
        if (!config.isVerificationConfigured()) {
            // No key can be found, so nothing is looked up.
            return AgentCardSignatureVerifier.verify(cardJson, (kid, jku) -> List.of(),
                    config.getAllowedAlgorithms());
        }
        return AgentCardSignatureVerifier.verify(cardJson, trustedKeys::keys, config.getAllowedAlgorithms());
    }

    /**
     * @return the distinct {@code kid}s of the card's signatures that verify now, or {@code null} when no
     *         key source is configured
     */
    public List<String> verifiedSigners(String cardJson) {
        if (!config.isVerificationConfigured() || cardJson == null) {
            return null;
        }
        String digest = DigestUtils.sha256Hex(cardJson);
        String cacheKey = digest + ":" + trustedKeys.epoch();
        synchronized (verifiedSignersCache) {
            List<String> cached = verifiedSignersCache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        List<String> verified = verify(cardJson).verifiedKids();
        // Cache only against the epoch the result was computed with: a key set refreshed meanwhile
        // (for an unknown kid) must not have its result stored under the older epoch.
        String computedKey = digest + ":" + trustedKeys.epoch();
        if (computedKey.equals(cacheKey)) {
            synchronized (verifiedSignersCache) {
                verifiedSignersCache.put(cacheKey, verified);
            }
        }
        return verified;
    }
}
