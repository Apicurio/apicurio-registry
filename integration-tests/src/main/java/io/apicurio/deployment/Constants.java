package io.apicurio.deployment;

import java.util.Optional;

/**
 * Constants used by the deployment infrastructure.
 */
public final class Constants {

    private Constants() {
    }

    /**
     * Registry image placeholder.
     */
    static final String REGISTRY_IMAGE = "registry-image";

    /**
     * Tag for auth tests profile.
     */
    static final String AUTH = "auth";

    /**
     * Tag for kafkasql tests profile.
     */
    static final String KAFKA_SQL = "kafkasqlit";

    /**
     * Tag for kafkasql snapshotting tests profile.
     */
    static final String KAFKA_SQL_SNAPSHOTTING =
            "kafkasql-snapshotting";

    /**
     * Tag for sql tests profile.
     */
    static final String SQL = "sqlit";

    /**
     * Tag for kubernetesops tests profile.
     */
    static final String KUBERNETES_OPS = "kubernetesopsit";

    /**
     * Tag for iceberg tests profile.
     */
    static final String ICEBERG = "iceberg";

    /**
     * Active test groups from the Maven groups property.
     */
    public static final String TEST_PROFILE =
            Optional.ofNullable(
                    System.getProperty("groups")).orElse("");
}
