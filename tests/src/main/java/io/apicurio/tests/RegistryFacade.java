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

    private static final String REGISTRY_JAR_PATH_FORMAT = "../app/target/apicurio-registry-app-%s-runner.jar";
    private static final String REGISTRY_JAR_PATH = System.getenv().get("REGISTRY_JAR_PATH");

    private Exec executor = new Exec();

    /**
     * Method for start registries from jar file. New process is created.
     */
    public void start() {


        CompletableFuture.supplyAsync(() -> {
            try {
                String path = REGISTRY_JAR_PATH;
                if (path == null) {
                    String version = System.getProperty("project.version"); // "1.2.0-SNAPSHOT";
                    path = String.format(REGISTRY_JAR_PATH_FORMAT, version);
                }
                if (path == null) {
                    throw new IllegalStateException("Could not determine where to find the executable jar for the server. " +
                        "This may happen if you are using an IDE to debug.");
                }
                LOGGER.info("Starting Registry app from: {}", path);

                int timeout = executor.execute("java",
                    // "-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005",
                    "-Dquarkus.http.port=8081",
                    "-Dquarkus.log.console.level=DEBUG",
                    "-Dquarkus.log.category.\"io\".level=DEBUG",
                    "-jar", path);
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
