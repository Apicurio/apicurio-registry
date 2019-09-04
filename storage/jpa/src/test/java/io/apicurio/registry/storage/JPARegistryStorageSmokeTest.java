/*
 * Copyright 2019 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage;

import javax.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import io.apicurio.registry.storage.impl.jpa.JPA;
import io.apicurio.registry.util.H2DatabaseService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class JPARegistryStorageSmokeTest extends AbstractRegistryStorageSmokeTest {

//    private static Logger log = LoggerFactory.getLogger(JPARegistryStorageSmokeTest.class);

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
