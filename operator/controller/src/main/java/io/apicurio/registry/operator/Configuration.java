package io.apicurio.registry.operator;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public class Configuration {

    private static final Config config = ConfigProvider.getConfig();

    private Configuration() {
    }

    public static String getAppImage() {
        // spotless:off
        return config.getOptionalValue("registry.app.image", String.class)
                .or(() -> config.getOptionalValue("related.image.registry.app.image", String.class))
                .orElseThrow(() -> new OperatorException("Required configuration option 'registry.app.image' is not set."));
        // spotless:on
    }

    public static String getUIImage() {
        // spotless:off
        return config.getOptionalValue("registry.ui.image", String.class)
                .or(() -> config.getOptionalValue("related.image.registry.ui.image", String.class))
                .orElseThrow(() -> new OperatorException("Required configuration option 'registry.ui.image' is not set."));
        // spotless:on
    }

    public static String getRegistryVersion() {
        // spotless:off
        return config.getOptionalValue("registry.version", String.class)
                .orElseThrow(() -> new OperatorException("Required configuration option 'registry.version' is not set."));
        // spotless:on
    }

    public static String getDefaultBaseHost() {
        // spotless:off
        return config.getOptionalValue("apicurio.operator.default-base-host", String.class)
                .map(v -> "." + v)
                .orElse("");
        // spotless:on
    }
}
