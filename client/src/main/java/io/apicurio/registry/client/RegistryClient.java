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

package io.apicurio.registry.client;

import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.rest.beans.VersionMetaData;
import io.apicurio.registry.types.ArtifactState;

import java.lang.reflect.Method;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.ws.rs.core.Response;

/**
 * @author Ales Justin
 */
public class RegistryClient {
    private static final Logger log = Logger.getLogger(RegistryClient.class.getName());

    private RegistryClient() {
    }

    public static RegistryService create(String baseUrl) {
        return new GenericClient.Builder<>(RegistryService.class).setBaseUrl(baseUrl)
                                                                 .setCustomMethods(RegistryClient::handleReset)
                                                                 .setResultConsumer(RegistryClient::handleResult)
                                                                 .build();
    }

    public static RegistryService cached(String baseUrl) {
        return cached(create(baseUrl));
    }

    public static RegistryService cached(RegistryService delegate) {
        return new CachedRegistryService(delegate);
    }

    private static Object handleReset(Method method, Object[] args) {
        if ("reset".equals(method.getName()) && (args == null || args.length == 0)) {
            return null;
        }
        return Void.class;
    }

    private static void handleResult(Object result) {
        if (result instanceof ArtifactMetaData) {
            ArtifactMetaData amd = (ArtifactMetaData) result;
            checkIfDeprecated(amd::getState, amd.getId(), amd.getVersion());
        } else if (result instanceof VersionMetaData) {
            VersionMetaData vmd = (VersionMetaData) result;
            checkIfDeprecated(vmd::getState, vmd.getId(), vmd.getVersion());
        } else if (result instanceof Response) {
            Response response = (Response) result;
            String isDeprecated = response.getHeaderString(Headers.DEPRECATED);
            if (isDeprecated != null) {
                String id = response.getHeaderString(Headers.ARTIFACT_ID);
                String version = response.getHeaderString(Headers.VERSION);
                checkIfDeprecated(() -> ArtifactState.DEPRECATED, id, version);
            }
        }
    }

    private static void checkIfDeprecated(Supplier<ArtifactState> stateSupplier, String artifactId, Object version) {
        if (stateSupplier.get() == ArtifactState.DEPRECATED) {
            log.warning(String.format("Artifact %s [%s] is deprecated", artifactId, version));
        }
    }
}
