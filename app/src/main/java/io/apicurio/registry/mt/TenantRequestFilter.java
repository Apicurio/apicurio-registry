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

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;

import io.apicurio.registry.mt.metadata.TenantMetadataService;
import io.apicurio.registry.types.Current;

/**
 * @author eric.wittmann@gmail.com
 */
@Provider
public class TenantRequestFilter implements ContainerRequestFilter {

    @Inject
    TenantContext tenantContext;

    @Inject
    @Current
    TenantMetadataService tenantMetadataService;

    /**
     * @see javax.ws.rs.container.ContainerRequestFilter#filter(javax.ws.rs.container.ContainerRequestContext)
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String tenantId = requestContext.getHeaderString("X-Registry-Tenant-Id");
        if (tenantId != null) {
            //TODO use the metadata
            tenantMetadataService.getTenantMetadata(tenantId);

            tenantContext.tenantId(tenantId);
        } else {
            tenantContext.clearTenantId();
        }
    }

}
