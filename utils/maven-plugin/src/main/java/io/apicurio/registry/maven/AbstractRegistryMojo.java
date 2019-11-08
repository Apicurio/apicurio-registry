/*
 * Copyright 2018 Confluent Inc.
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
import io.apicurio.registry.utils.IoUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Ales Justin
 */
public abstract class AbstractRegistryMojo extends AbstractMojo {

    @Parameter(required = true)
    String registryUrl;

    private RegistryService client;

    protected RegistryService getClient() {
        if (client == null) {
            this.client = RegistryClient.cached(registryUrl);
        }
        return this.client;
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

    protected Map<String, byte[]> loadArtifacts(Map<String, File> artifacts) {
        int errorCount = 0;
        Map<String, byte[]> results = new LinkedHashMap<>();

        for (Map.Entry<String, File> kvp : artifacts.entrySet()) {
            getLog().debug(
                String.format(
                    "Loading artifact for id [%s] from %s.",
                    kvp.getKey(),
                    kvp.getValue()
                )
            );

            try (FileInputStream inputStream = new FileInputStream(kvp.getValue())) {
                results.put(kvp.getKey(), IoUtil.toBytes(inputStream));
            } catch (IOException ex) {
                getLog().error("Exception while loading " + kvp.getValue(), ex);
                errorCount++;
            }
        }

        if (errorCount > 0) {
            throw new IllegalStateException("One or more artifacts could not be loaded.");
        }

        return results;
    }
}
