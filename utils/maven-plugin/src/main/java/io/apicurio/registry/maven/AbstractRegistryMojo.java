/*
 * Copyright 2018 Confluent Inc. (adapted from their Mojo)
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

package io.apicurio.registry.maven;

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Base class for all Registry Mojo's.
 * It handles RegistryService's (aka client) lifecycle.
 *
 * @author Ales Justin
 */
public abstract class AbstractRegistryMojo extends AbstractMojo {

    /**
     * The registry's url.
     * e.g. http://localhost:8080/api
     */
    @Parameter(required = true)
    String registryUrl;

    private RegistryService client;

    protected RegistryService getClient() {
        if (client == null) {
            client = RegistryClient.cached(registryUrl);
        }
        return client;
    }

    protected void setClient(RegistryService client) {
        this.client = client;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            executeInternal();
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    protected abstract void executeInternal() throws MojoExecutionException, MojoFailureException;
}
