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

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.multitenant.api.datamodel.RegistryDeploymentInfo;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.client.OpenShiftClient;

/**
 * This service provides information about the registry deployment paired with this tenant manager.
 * The tenant manager can know what registry deployment is paired with via different ways.
 * Via "registry.route.url" config property or via "registry.route.name", with the latter the tenant manager
 * will use the OpenshiftClient to query the Openshift cluster to get the registry deployment url.
 *
 * @author Fabian Martinez
 */
@ApplicationScoped
public class RegistryDeploymentInfoService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @ConfigProperty(name = "registry.namespace")
    Optional<String> registryNamespace;

    @ConfigProperty(name = "registry.route.name")
    Optional<String> registryRouteName;

    @ConfigProperty(name = "registry.route.url")
    Optional<String> registryRouteUrl;

    @Inject
    OpenShiftClient openshiftClient;

    private RegistryDeploymentInfo deploymentInfo;

    public RegistryDeploymentInfo getRegistryDeploymentInfo() {
        if (deploymentInfo == null) {
            RegistryDeploymentInfo info = new RegistryDeploymentInfo();

            if (registryRouteUrl.isPresent()) {
                info.setUrl(registryRouteUrl.get());
                info.setName(info.getUrl());
            } else if (registryRouteName.isPresent()) {
                var routes = openshiftClient.routes();

                if (registryNamespace.isEmpty()) {
                    log.info("registry.namespace not present, using default OpenshiftClient namespace from context");
                } else {
                    routes.inNamespace(registryNamespace.get());
                }

                Route route = routes.withName(registryRouteName.get()).get();

                if (route != null) {
                    info.setUrl((route.getSpec().getTls() == null ? "http://" : "https://") + route.getSpec().getHost());
                    info.setName(route.getMetadata().getName());
                }
            }
            if (info.getUrl() != null) {
                deploymentInfo = info;
            }
        }
        return deploymentInfo;
    }

}
