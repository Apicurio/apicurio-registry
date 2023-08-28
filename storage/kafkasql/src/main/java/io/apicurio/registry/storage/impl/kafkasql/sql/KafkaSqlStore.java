package io.apicurio.registry.storage.impl.kafkasql.sql;

import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.storage.impl.sql.AbstractSqlRegistryStorage;
import io.apicurio.registry.storage.impl.sql.HandleFactory;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * The SQL store used by the KSQL registry artifactStore implementation.  This is ultimately where each
 * application replica stores its data after consuming it from the Kafka topic.  Often this is a
 * H2 in-memory database, but it could be something else (e.g. a local postgresql sidecar DB).
 * This class extends the core SQL registry artifactStore to leverage as much of the existing SQL
 * support as possible.
 *
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
@Logged
public class KafkaSqlStore extends AbstractSqlRegistryStorage {


    @Inject
    HandleFactory handleFactory;


    @PostConstruct
    void postConstruct() {
        initialize(handleFactory, false);
    }


    void onStart(@Observes StartupEvent ev) {
        // Do nothing, just force initialization of the bean.
        // Otherwise, there are some corner cases where KafkaSqlRegistryStorage does not become ready,
        // because it never receives the io.apicurio.registry.storage.impl.sql.SqlStorageEvent.
        // This can be reproduced by removing this method and running
        // io.apicurio.tests.dbupgrade.KafkaSqlLogCompactionIT.testLogCompaction test.
    }
}
