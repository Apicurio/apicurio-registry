/*
 * Copyright 2019 JBoss Inc
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

package io.apicurio.registry.storage.util;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class H2DatabaseResourceManager implements QuarkusTestResourceLifecycleManager {
    private static final Logger log = LoggerFactory.getLogger(H2DatabaseResourceManager.class);

    private Server tcpServer;

    @Override
    public Map<String, String> start() {
        log.info("Starting H2 ...");


        try {
            tcpServer = Server.createTcpServer("-tcpPort", "9123");
            tcpServer.start();
            System.out.println("[INFO] H2 database started in TCP server mode");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return defaultH2Configuration();
    }

    private Map<String, String> defaultH2Configuration() {
        final Map<String, String> configuration = new HashMap<>();

        configuration.put("quarkus.datasource.url", "jdbc:h2:tcp://localhost/mem:test_quarkus;DB_CLOSE_DELAY=-1");
        configuration.put("quarkus.datasource.driver", "org.h2.Driver");
        configuration.put("quarkus.datasource.username", "sa");
        configuration.put("quarkus.datasource.password", "sa");

        return configuration;
    }

    @Override
    public void stop() {
        log.info("Stopping H2 ...");
        if (tcpServer != null) {
            tcpServer.stop();
            tcpServer = null;
        }
    }
}
