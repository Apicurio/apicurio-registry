/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.ccompat.rest.v6.impl;

import io.apicurio.registry.ccompat.dto.ModeDto;
import io.apicurio.registry.ccompat.rest.v6.ModeResource;
import io.apicurio.registry.ccompat.rest.error.Errors;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;

import javax.interceptor.Interceptors;

/**
 * We <b>DO NOT</b> support this endpoint. Fails with 404.
 *
 * @author Ales Justin
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Interceptors({ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class})
@Logged
public class ModeResourceImpl extends AbstractResource implements ModeResource {


    @Override
    public ModeDto getGlobalMode() {
        Errors.operationNotSupported();
        return null;
    }


    @Override
    public ModeDto updateGlobalMode(ModeDto request) {
        Errors.operationNotSupported();
        return null;
    }
}
