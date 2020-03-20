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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class RegistryFacade {
    static final Logger LOGGER = LoggerFactory.getLogger(RegistryFacade.class);

    public static final String DEFAULT_REGISTRY_JAR_PATH = "../app/target/apicurio-registry-app-1.1.2-SNAPSHOT-runner.jar";
    public static final String REGISTRY_JAR_PATH = System.getenv().getOrDefault("REGISTRY_JAR_PATH", DEFAULT_REGISTRY_JAR_PATH);

    private Exec executor = new Exec();

    /**
     * Method for start registries from jar file. New process is created.
     */
    public void start() {
        LOGGER.info("Starting Registry app from: {}", REGISTRY_JAR_PATH);

        CompletableFuture.supplyAsync(() -> {
            try {
                int timeout = executor.execute("java", "-Dquarkus.http.port=8081", "-Dquarkus.log.console.level=DEBUG", "-Dquarkus.log.category.\"io\".level=DEBUG", "-jar", REGISTRY_JAR_PATH);
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
}
