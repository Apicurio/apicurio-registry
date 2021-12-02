/*
 * Copyright 2021 Red Hat
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
package io.apicurio.multitenant.api.services;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import io.apicurio.multitenant.api.datamodel.RegistryDeploymentInfo;

/**
 * This service provides information about the registry deployment paired with this tenant manager.
 * The tenant manager can know what registry deployment is paired with via "registry.route.url" config property.
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class RegistryDeploymentInfoService {

    @Inject
    Logger log;

    @ConfigProperty(name = "registry.route.url")
    String registryRouteUrl;

    private RegistryDeploymentInfo deploymentInfo;

    public RegistryDeploymentInfo getRegistryDeploymentInfo() {
        if (deploymentInfo == null) {
            RegistryDeploymentInfo info = new RegistryDeploymentInfo();
            info.setUrl(registryRouteUrl);
            info.setName(info.getUrl());
            deploymentInfo = info;
        }
        return deploymentInfo;
    }

}
