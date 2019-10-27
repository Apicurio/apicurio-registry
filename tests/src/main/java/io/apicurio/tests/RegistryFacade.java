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
package io.apicurio.tests;

import io.apicurio.tests.executor.Exec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;

public class  RegistryFacade {
    static final Logger LOGGER = LoggerFactory.getLogger(RegistryFacade.class);

    public static final String DEFAULT_REGISTRY_JAR_PATH = "../app/target/apicurio-registry-app-1.0.2-SNAPSHOT-runner.jar";
    public static final String DEFAULT_REGISTRY_PORT = "8080";
    public static final String DEFAULT_REGISTRY_URL = "localhost";

    public static final String REGISTRY_JAR_PATH = System.getenv().getOrDefault("REGISTRY_JAR_PATH", DEFAULT_REGISTRY_JAR_PATH);
    public static final String REGISTRY_PORT = System.getenv().getOrDefault("REGISTRY_PORT", DEFAULT_REGISTRY_PORT);
    public static final String REGISTRY_URL = System.getenv().getOrDefault("REGISTRY_URL", DEFAULT_REGISTRY_URL);
    public static final String EXTERNAL_REGISTRY = System.getenv().getOrDefault("EXTERNAL_REGISTRY", "");

    private Exec executor = new Exec();

    private Future<Boolean> registries;

    /**
     * Method for start registries from jar file. New process is created.
     */
    public void start() {
        LOGGER.info("Starting Registry app from: {}", REGISTRY_JAR_PATH);

        registries = CompletableFuture.supplyAsync(() -> {
            try {
                int timeout = executor.execute("java", "-Dquarkus.log.console.level=DEBUG", "-Dquarkus.log.category.\"io\".level=DEBUG", "-jar", REGISTRY_JAR_PATH);
                return timeout == 0;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, runnable -> new Thread(runnable).start());
    }

    /**
     * Method for stop registries process.
     */
    public void stop() {
        if (executor != null) {
            executor.stop();
        }
    }

    public String getRegistryStdOut() {
        return executor.stdOut();
    }

    public String getRegistryStdErr() {
        return executor.stdErr();
    }

    /**
     * Method which try connection to registries. It's used as a initial check for registries availability.
     * @return true if registries are ready for use, false in other cases
     */
    public static boolean isReachable() {
        LOGGER.info("Trying to connect to {}:{}", REGISTRY_URL, REGISTRY_PORT);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(REGISTRY_URL, Integer.parseInt(REGISTRY_PORT)), 5_000);
            LOGGER.info("Client is able to connect to the selenium hub");
            return  true;
        } catch (IOException ex) {
            LOGGER.warn("Cannot connect to hub: {}", ex.getMessage());
            return false; // Either timeout or unreachable or failed DNS lookup.
        }
    }
}
