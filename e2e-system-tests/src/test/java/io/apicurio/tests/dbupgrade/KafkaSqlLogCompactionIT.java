/*
 * Copyright 2023 Red Hat
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

package io.apicurio.tests.dbupgrade;

import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;

import java.util.Collections;
import java.util.Map;

/**
 * @author Fabian Martinez
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@Tag(Constants.DB_UPGRADE)
@Tag(Constants.KAFKA_SQL)
@QuarkusTestResource(value = KafkaSqlLogCompactionIT.KafkaSqlLogCompactionTestInitializer.class, restrictToAnnotatedClass = true)
@QuarkusIntegrationTest
public class KafkaSqlLogCompactionIT implements TestSeparator, Constants {

    final Logger logger = LoggerFactory.getLogger(getClass());


    @Test
    public void testLogCompaction() throws Exception {


    }

    public static class KafkaSqlLogCompactionTestInitializer implements QuarkusTestResourceLifecycleManager {

        GenericContainer genericContainer;



        @Override
        public void init(Map<String, String> initArgs) {

        }

        @Override
        public Map<String, String> start() {
            genericContainer = new GenericContainer("quay.io/apicurio/apicurio-registry-kafkasql:2.1.2.Final")
                    .withEnv(Map.of("KAFKA_BOOTSTRAP_SERVERS", System.getProperty("bootstrap.servers"), "QUARKUS_HTTP_PORT", "8081"))
                    .withNetworkMode("host");

            genericContainer.start();

            genericContainer.waitingFor(Wait.forLogMessage(".*(KSQL Kafka Consumer Thread) KafkaSQL storage bootstrapped.*", 1));

            return Collections.emptyMap();
        }

        @Override
        public void stop() {
            if (genericContainer != null && genericContainer.isRunning()) {
                genericContainer.stop();
            }
        }
    }
}
