/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.cli;

import io.apicurio.registry.client.RegistryRestClient;
import io.apicurio.registry.client.RegistryRestClientFactory;
import picocli.CommandLine;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ales Justin
 */
public abstract class AbstractCommand implements Runnable {
    protected Logger log = Logger.getLogger(getClass().getName());

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"-d", "--debug"})
    boolean debug;

    @CommandLine.Option(names = {"-ll", "--logLevel"}, description = "Set log level to enable logging")
    Level level;

    @CommandLine.Option(names = {"-url", "--url"}, description = "Registry url", defaultValue = "http://localhost:8080/api")
    String url;

    private static RegistryRestClient client; // single stateless client instance for all commands

    protected void println(Object value) {
        if (level != null) {
            log.log(level, String.valueOf(value));
        } else {
            System.out.println(value);
        }
    }

    protected RegistryRestClient getClient() {
        if (client == null) {
            log.info("Connecting to registry at " + url + "\n");
            client = RegistryRestClientFactory.create(url);
        }
        return client;
    }
}
