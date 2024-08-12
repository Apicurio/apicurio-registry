package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.api.v3.v1.ApicurioRegistry3;

public class LogUtils {

    public static String contextPrefix(ApicurioRegistry3 primary) {
        return "[%s:%s] ".formatted(primary.getMetadata().getNamespace(), primary.getMetadata().getName());
    }
}
