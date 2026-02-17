package io.apicurio.registry.rest.cache;

import io.apicurio.common.apps.config.Info;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_CACHE;

@ApplicationScoped
public class HttpCachingConfig {

    @ConfigProperty(name = "apicurio.http-caching.enabled", defaultValue = "false")
    @Info(category = CATEGORY_CACHE, description = """
            Enable or disable HTTP caching entirely. \
            When disabled, no server-side cache headers (ETag, Surrogate-Control, etc.) are sent, and cache strategies are not evaluated. \
            Caching is automatically disabled if all max-age configurations are set to <= 0.\
            """, availableSince = "3.2.0")
    boolean enabled;

    public boolean isCachingEnabled() {
        // @formatter:off
        return enabled && (
                highExpirationSeconds > 0 ||
                moderateExpirationSeconds > 0 ||
                lowExpirationSeconds > 0
        );
        // @formatter:on
    }

    @ConfigProperty(name = "apicurio.http-caching.high-cacheability.max-age-seconds", defaultValue = "864000" /* 10 days */)
    @Info(category = CATEGORY_CACHE, description = """
            Expiration for high cacheability operations, in seconds. \
            If set to <= 0, caching is disabled for these operations.\
            """, availableSince = "3.2.0")
    long highExpirationSeconds;

    public Duration getHighExpiration() {
        return Duration.ofSeconds(highExpirationSeconds);
    }

    @ConfigProperty(name = "apicurio.http-caching.moderate-cacheability.max-age-seconds", defaultValue = "30")
    @Info(category = CATEGORY_CACHE, description = """
            Expiration for moderate cacheability operations, in seconds. \
            If set to <= 0, caching is disabled for these operations.\
            """, availableSince = "3.2.0")
    long moderateExpirationSeconds;

    public Duration getModerateExpiration() {
        return Duration.ofSeconds(moderateExpirationSeconds);
    }

    @ConfigProperty(name = "apicurio.http-caching.low-cacheability.max-age-seconds", defaultValue = "10")
    @Info(category = CATEGORY_CACHE, description = """
            Expiration for low cacheability operations, in seconds. \
            If set to <= 0, caching is disabled for these operations.\
            """, availableSince = "3.2.0")
    long lowExpirationSeconds;

    public Duration getLowExpiration() {
        return Duration.ofSeconds(lowExpirationSeconds);
    }

    @ConfigProperty(name = "apicurio.http-caching.higher-quality-etags-enabled", defaultValue = "true")
    @Info(category = CATEGORY_CACHE, description = """
            Provide higher quality ETags if possible, but they might be more expensive to compute. \
            This feature trades more computation for potentially higher cacheability of some operations. \
            """, availableSince = "3.2.0")
    @Getter
    boolean higherQualityETagsEnabled;

    @ConfigProperty(name = "apicurio.http-caching.extra-headers-enabled", defaultValue = "true")
    @Info(category = CATEGORY_CACHE, description = """
            Send additional informational headers to help understand caching behavior. \
            Extra headers include X-Cache-Cacheability which indicates the evaluated cacheability level. \
            These headers are useful for development, troubleshooting, and making additional decisions in the cache configuration.\
            """, availableSince = "3.2.0")
    @Getter
    boolean extraHeadersEnabled;

    @ConfigProperty(name = "apicurio.http-caching.opaque-etags-enabled")
    @Info(category = CATEGORY_CACHE, description = """
            Hash raw ETag values before adding them to the header. \
            Enabling this feature might marginally increase security, while disabling is useful for testing and debugging. \
            If no value is provided, the feature is disabled when the `prod` profile is active.\
            """, availableSince = "3.2.0")
    Optional<Boolean> opaqueETagsEnabled;

    public boolean getOpaqueETagsEnabled() {
        return opaqueETagsEnabled.orElse(ConfigUtils.isProfileActive("prod"));
    }
}
