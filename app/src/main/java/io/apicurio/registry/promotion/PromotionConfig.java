package io.apicurio.registry.promotion;

import io.apicurio.common.apps.config.Info;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static io.apicurio.common.apps.config.ConfigPropertyCategory.CATEGORY_REST;

/**
 * Discovers named promotion sources from {@code apicurio.promotion.source.{name}.*} properties.
 */
@Singleton
public class PromotionConfig {

    static final String SOURCE_PREFIX = "apicurio.promotion.source.";
    static final String URL_SUFFIX = ".url";

    @ConfigProperty(name = "apicurio.promotion.enabled", defaultValue = "true")
    @Info(category = CATEGORY_REST, description = "Enables the cross-registry promotion REST API", availableSince = "3.3.2")
    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public List<PromotionSourceDefinition> listSources() {
        Config config = ConfigProvider.getConfig();
        List<PromotionSourceDefinition> sources = new ArrayList<>();
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(SOURCE_PREFIX) && propertyName.endsWith(URL_SUFFIX)) {
                String name = propertyName.substring(SOURCE_PREFIX.length(),
                        propertyName.length() - URL_SUFFIX.length());
                if (!name.isBlank() && !name.contains(".")) {
                    sources.add(readSource(config, name));
                }
            }
        }
        sources.sort(Comparator.comparing(PromotionSourceDefinition::name));
        return sources;
    }

    public PromotionSourceDefinition requireSource(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Promotion source name is required");
        }
        return listSources().stream().filter(source -> source.name().equals(name)).findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Unknown promotion source '" + name + "'. Configure apicurio.promotion.source."
                                + name + ".url"));
    }

    private PromotionSourceDefinition readSource(Config config, String name) {
        String base = SOURCE_PREFIX + name + ".";
        String url = config.getOptionalValue(base + "url", String.class).orElseThrow();
        return new PromotionSourceDefinition(name, url.trim(),
                optional(config, base + "auth").orElse("none"),
                optional(config, base + "token").orElse(null),
                optional(config, base + "username").orElse(null),
                optional(config, base + "password").orElse(null),
                optional(config, base + "token-url").orElse(null),
                optional(config, base + "client-id").orElse(null),
                optional(config, base + "client-secret").orElse(null));
    }

    private static Optional<String> optional(Config config, String name) {
        return config.getOptionalValue(name, String.class).map(String::trim).filter(v -> !v.isEmpty());
    }
}
