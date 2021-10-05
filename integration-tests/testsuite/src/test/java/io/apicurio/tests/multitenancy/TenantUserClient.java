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

package io.apicurio.tests.multitenancy;

import io.apicurio.registry.rest.client.RegistryClient;

/**
 * @author Fabian Martinez
 */
public class TenantUserClient {

    public final TenantUser user;
    public final String tenantAppUrl;
    public final RegistryClient client;
    public final String tokenEndpoint;

    public TenantUserClient(TenantUser user, String tenantAppUrl, RegistryClient client, String tokenEndpoint) {
        super();
        this.user = user;
        this.tenantAppUrl = tenantAppUrl;
        this.client = client;
        this.tokenEndpoint = tokenEndpoint;
    }

}
