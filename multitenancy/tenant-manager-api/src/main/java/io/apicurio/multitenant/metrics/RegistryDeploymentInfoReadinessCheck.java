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
package io.apicurio.multitenant.metrics;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.apicurio.multitenant.api.datamodel.RegistryDeploymentInfo;
import io.apicurio.multitenant.api.services.RegistryDeploymentInfoService;

/**
 *
 * @author Fabian Martinez
 *
 */
@ApplicationScoped
@Readiness
public class RegistryDeploymentInfoReadinessCheck implements HealthCheck {

    private static final String CHECK_NAME = "RegistryDeploymentInfo";

    @Inject
    RegistryDeploymentInfoService deploymentInfoService;

    @Override
    public HealthCheckResponse call() {
        try {
            RegistryDeploymentInfo deploymentInfo = deploymentInfoService.getRegistryDeploymentInfo();
            return HealthCheckResponse.builder()
                    .name(CHECK_NAME)
                    .status(deploymentInfo.getUrl() != null)
                    .withData("registryName", deploymentInfo.getName())
                    .withData("registryUrl", deploymentInfo.getUrl())
                    .build();
        } catch (Exception e) {
            return HealthCheckResponse.builder()
                    .name(CHECK_NAME)
                    .down()
                    .withData("error", e.getMessage())
                    .build();
        }
    }

}
