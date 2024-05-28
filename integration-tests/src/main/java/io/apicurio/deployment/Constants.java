package io.apicurio.deployment;

import java.util.Optional;

public class Constants {

    /**
     * Registry image placeholder
     */
    static final String REGISTRY_IMAGE = "registry-image";

    /**
     * Tag for auth tests profile.
     */
    static final String AUTH = "auth";

    /**
     * Tag for sql db upgrade tests profile.
     */
    static final String KAFKA_SQL = "kafkasqlit";

    /**
     * Tag for sql db upgrade tests profile.
     */
    static final String KAFKA_SQL_SNAPSHOTTING = "kafkasql-snapshotting";

    /**
     * Tag for sql db upgrade tests profile.
     */
    static final String SQL = "sqlit";


    public static final String TEST_PROFILE =
            Optional.ofNullable(System.getProperty("groups"))
                    .orElse("");
}
