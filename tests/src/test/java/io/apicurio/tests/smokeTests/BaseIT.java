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

package io.apicurio.tests.smokeTests;

import io.apicurio.registry.util.H2DatabaseService;
import io.apicurio.tests.KafkaFacade;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class BaseIT {

    static final Logger LOGGER = LogManager.getLogger(BaseIT.class);
    static long timeout = 5L;

    static Vertx vertx;
    static WebClient client;

    private static H2DatabaseService h2ds = new H2DatabaseService();

    static KafkaFacade kafkaCluster = new KafkaFacade();

    @BeforeAll
    static void beforeAll() throws Exception {
        h2ds.start();
        kafkaCluster.start();
        vertx = Vertx.vertx();

        client = WebClient.create(vertx, new WebClientOptions()
                .setDefaultHost("0.0.0.0")
                .setDefaultPort(8080)
        );
    }

    @AfterAll
    static void afterAll() {
        kafkaCluster.stop();
        vertx.close();
        h2ds.stop();
    }
}
