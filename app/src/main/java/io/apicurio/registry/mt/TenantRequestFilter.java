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

package io.apicurio.registry.mt;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * @author eric.wittmann@gmail.com
 */
@PreMatching
@Priority(0)
@Provider
public class TenantRequestFilter implements ContainerRequestFilter {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Inject
    TenantIdResolver tenantIdResolver;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {

        tenantIdResolver.rewriteTenantRequest(requestContext);

    }

}