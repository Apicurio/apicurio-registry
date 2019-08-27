package io.apicurio.registry.storage;

import io.apicurio.registry.storage.impl.jpa.JPA;
import io.apicurio.registry.util.H2DatabaseService;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

@QuarkusTest
public class JPARegistryStorageSmokeTest extends AbstractRegistryStorageSmokeTest {

    private static Logger log = LoggerFactory.getLogger(JPARegistryStorageSmokeTest.class);

    private static H2DatabaseService h2ds = new H2DatabaseService();

    @BeforeAll
    public static void beforeAll() throws Exception {
        h2ds.start();
    }

    @AfterAll
    public static void afterAll() {
        h2ds.stop();
    }

    @Inject
    @JPA
    RegistryStorage storage;

    @Override
    RegistryStorage getStorage() {
        return storage;
    }
}
