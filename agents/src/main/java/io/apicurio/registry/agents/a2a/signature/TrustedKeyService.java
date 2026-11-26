package io.apicurio.registry.agents.a2a.signature;

import io.apicurio.registry.utils.StringUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.codec.digest.DigestUtils;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The trusted public keys: the JSON Web Key Sets of {@code apicurio.a2a.signature-verification.jwks-urls},
 * and the key set named by a signature's {@code jku} when its host is in
 * {@code apicurio.a2a.signature-verification.jku-allowlist}. A {@code jku} on any other host is never
 * fetched.
 * <p>
 * Each key set is fetched when first needed and again once it is older than
 * {@code apicurio.a2a.signature-verification.cache-ttl.seconds}. A signature naming a {@code kid} found in
 * no key set refreshes the key sets early, at most once per key set in that period, so rotated keys are
 * picked up without letting unknown {@code kid}s trigger a fetch each. A key set that cannot be fetched
 * contributes no keys until it is fetched successfully: verification fails closed.
 * </p>
 * <p>
 * {@link #epoch()} changes whenever the content of any key set changes, so results computed from the keys
 * can be cached against it.
 * </p>
 */
@ApplicationScoped
public class TrustedKeyService {

    private static final Logger log = LoggerFactory.getLogger(TrustedKeyService.class);

    /** Upper bound on the distinct {@code jku} key sets kept, so signatures cannot grow the cache. */
    static final int MAX_JKU_SOURCES = 64;

    @Inject
    AgentCardSignatureConfig config;

    @Inject
    JwksFetcher fetcher;

    private final Map<String, KeySource> configuredSources = new ConcurrentHashMap<>();
    private final Map<String, KeySource> jkuSources = new ConcurrentHashMap<>();
    private final AtomicLong epoch = new AtomicLong();

    /**
     * @return the trusted keys with this {@code kid}, refreshing the key sets once, within the rate limit,
     *         when none has it
     */
    public List<JsonWebKey> keys(String kid, String jku) {
        List<KeySource> sources = sourcesFor(jku);
        List<JsonWebKey> found = matching(sources, kid, false);
        if (found.isEmpty()) {
            found = matching(sources, kid, true);
        }
        return found;
    }

    /**
     * Refreshes the key sets whose time-to-live has passed, then returns the epoch: a value that changes
     * whenever the content of a key set changes.
     */
    public long epoch() {
        for (String url : config.getJwksUrls()) {
            configuredSource(url).current(false);
        }
        jkuSources.values().forEach(source -> source.current(false));
        return epoch.get();
    }

    private List<JsonWebKey> matching(List<KeySource> sources, String kid, boolean refreshOnMiss) {
        List<JsonWebKey> found = new ArrayList<>();
        for (KeySource source : sources) {
            for (JsonWebKey key : source.current(refreshOnMiss)) {
                if (kid.equals(key.getKeyId())) {
                    found.add(key);
                }
            }
        }
        return found;
    }

    private List<KeySource> sourcesFor(String jku) {
        List<KeySource> sources = new ArrayList<>();
        for (String url : config.getJwksUrls()) {
            sources.add(configuredSource(url));
        }
        if (jku != null && isAllowedJku(jku)) {
            KeySource source = jkuSources.get(jku);
            if (source == null && jkuSources.size() < MAX_JKU_SOURCES) {
                source = jkuSources.computeIfAbsent(jku, KeySource::new);
            }
            if (source != null) {
                sources.add(source);
            } else {
                log.warn("Not following an allow-listed 'jku': {} key sets are already cached.", MAX_JKU_SOURCES);
            }
        }
        return sources;
    }

    private KeySource configuredSource(String url) {
        return configuredSources.computeIfAbsent(url, KeySource::new);
    }

    /**
     * @return whether the {@code jku} names a host of the allow-list: an exact host name, or a subdomain of
     *         a {@code *.domain} entry. Nothing that does not parse as a URL with a host is allowed.
     */
    boolean isAllowedJku(String jku) {
        String host;
        try {
            host = new URI(jku).getHost();
        } catch (Exception e) {
            return false;
        }
        if (StringUtil.isEmpty(host)) {
            return false;
        }
        String name = host.toLowerCase(Locale.ROOT);
        if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        for (String entry : config.getJkuAllowlist()) {
            String allowed = entry.toLowerCase(Locale.ROOT);
            if (allowed.startsWith("*.")) {
                String suffix = allowed.substring(1);
                if (name.endsWith(suffix) && name.length() > suffix.length()) {
                    return true;
                }
            } else if (name.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One key set URL and its last fetch.
     */
    private final class KeySource {

        private final String url;
        private volatile List<JsonWebKey> keys = List.of();
        private volatile String fingerprint;
        private volatile long fetchedAt;
        private volatile long earlyRefreshAt;
        private volatile boolean earlyRefreshed;
        private volatile boolean fetched;

        private KeySource(String url) {
            this.url = url;
        }

        List<JsonWebKey> current(boolean refreshOnMiss) {
            long now = System.nanoTime();
            long ttl = config.getCacheTtlSeconds() * 1_000_000_000L;
            if (!fetched || now - fetchedAt >= ttl) {
                refresh(false);
            } else if (refreshOnMiss && (!earlyRefreshed || now - earlyRefreshAt >= ttl)) {
                refresh(true);
            }
            return keys;
        }

        private synchronized void refresh(boolean early) {
            long now = System.nanoTime();
            long ttl = config.getCacheTtlSeconds() * 1_000_000_000L;
            // Another thread may have refreshed while this one waited.
            boolean recent = early ? earlyRefreshed && now - earlyRefreshAt < ttl : now - fetchedAt < ttl;
            if (fetched && recent) {
                return;
            }
            if (early) {
                earlyRefreshAt = now;
                earlyRefreshed = true;
            }
            List<JsonWebKey> newKeys;
            String newFingerprint;
            try {
                String body = fetcher.fetch(url);
                newKeys = List.copyOf(new JsonWebKeySet(body).getJsonWebKeys());
                newFingerprint = DigestUtils.sha256Hex(body);
            } catch (JwksFetcher.JwksFetchException | JoseException | RuntimeException e) {
                log.warn("Could not load the JSON Web Key Set {}: {}. Its keys are not trusted until it loads.",
                        url, e instanceof JoseException ? "not a valid JSON Web Key Set" : e.getMessage());
                newKeys = List.of();
                newFingerprint = null;
            }
            keys = newKeys;
            fetchedAt = System.nanoTime();
            fetched = true;
            if (!Objects.equals(fingerprint, newFingerprint)) {
                fingerprint = newFingerprint;
                epoch.incrementAndGet();
            }
        }
    }
}
