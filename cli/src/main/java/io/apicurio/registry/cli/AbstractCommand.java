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

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import picocli.CommandLine;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * @author Ales Justin
 */
public abstract class AbstractCommand implements Runnable {

    /**
     * Use this logger to log all debug traces, it will be printed if the --logLevel is set
     */
    protected Logger log = Logger.getLogger(getClass().getName());

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"-ll", "--logLevel"}, description = "Set log level to enable logging")
    void setLevel(Level level) {
        log.setLevel(level);
    }

    @CommandLine.Option(names = {"-url", "--url"}, description = "Registry url", defaultValue = "http://localhost:8080/apis/registry/v2")
    String url;

    private static RegistryClient client; // single stateless client instance for all commands

    protected static final ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    /**
     * For printing the result of the commands we just use the standard output. Use the logger above for debug traces.
     */
    protected void println(Object value) {
        System.out.println(value.toString());
    }

    protected RegistryClient getClient() {
        if (client == null) {
            log.info("Connecting to registry at " + url + "\n");
            client = RegistryClientFactory.create(url);
        }
        return client;
    }
}
