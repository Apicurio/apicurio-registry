package io.apicurio.registry.operator;

import org.eclipse.microprofile.config.ConfigProvider;

public class Configuration {

    private Configuration() {
    }

    public static String getAppImage() {
        return ConfigProvider.getConfig().getOptionalValue("registry.app.image", String.class)
                .orElseThrow(() -> new OperatorException(
                        "Required configuration option 'registry.app.image' is not set."));
    }

    public static String getUIImage() {
        return ConfigProvider.getConfig().getOptionalValue("registry.ui.image", String.class).orElseThrow(
                () -> new OperatorException("Required configuration option 'registry.ui.image' is not set."));
    }

    public static String getDefaultBaseHost() {
        return ConfigProvider.getConfig()
                .getOptionalValue("apicurio.operator.default-base-host", String.class).map(v -> "." + v)
                .orElse("");
    }
}
