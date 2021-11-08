/*
 * Copyright 2018 Confluent Inc. (adapted from their Mojo)
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

package io.apicurio.registry.maven;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.auth.Auth;
import io.apicurio.registry.auth.BasicAuth;
import io.apicurio.registry.auth.KeycloakAuth;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all Registry Mojo's.
 * It handles RegistryService's (aka client) lifecycle.
 *
 * @author Ales Justin
 */
public abstract class AbstractRegistryMojo extends AbstractMojo {

    public static class FileArtifact<T> {
        public FileArtifact(List<T> artifacts) {
            this.artifacts = artifacts;
        }

        @JsonProperty
        List<T> artifacts;

        public List<T> getArtifacts() {
            return artifacts;
        }

        public void setArtifacts(List<T> artifacts) {
            this.artifacts = artifacts;
        }
    }

    /**
     * The registry's url.
     * e.g. http://localhost:8080/api
     */
    @Parameter(required = true)
    String registryUrl;

    @Parameter
    String authServerUrl;

    @Parameter
    String realm;

    @Parameter
    String clientId;

    @Parameter
    String clientSecret;

    @Parameter
    String username;

    @Parameter
    String password;

    private static RegistryClient client;

    protected RegistryClient getClient() {
        if (client == null) {
            if (authServerUrl != null && realm != null && clientId != null && clientSecret != null) {
                Auth auth = new KeycloakAuth(authServerUrl, realm, clientId, clientSecret);
                client = RegistryClientFactory.create(registryUrl, Collections.emptyMap(), auth);
            } else if (username != null && password != null) {
                Auth auth = new BasicAuth(username, password);
                client = RegistryClientFactory.create(registryUrl, Collections.emptyMap(), auth);
            } else {
                client = RegistryClientFactory.create(registryUrl);
            }
        }
        return client;
    }

    public <T> FileArtifact<T> parseArtifacts(final String configPath, Class<T> clazz) throws MojoExecutionException {
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        List<T> artifacts = null;
        FileArtifact<T> fileArtifacts=null;
        try {
            artifacts = mapper.readValue(new File(configPath), (Class<List<T>>) (Object) List.class);
            fileArtifacts = new FileArtifact(artifacts);
        } catch (Exception ex) {
            throw new MojoExecutionException("Error while parsing artifacts config file");
        }
        return fileArtifacts;
    }


    protected void setClient(RegistryClient client) {
        AbstractRegistryMojo.client = client;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        executeInternal();
    }

    protected abstract void executeInternal() throws MojoExecutionException, MojoFailureException;
}
