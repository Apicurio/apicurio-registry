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

/**
 * @author Fabian Martinez
 */
public class TenantUser {

    public final String tenantId;
    public final String principalId;
    public final String organizationId;
    public final String principalPassword;

    public TenantUser(String tenantId, String principalId, String organizationId, String principalPassword) {
        this.tenantId = tenantId;
        this.principalId = principalId;
        this.organizationId = organizationId;
        this.principalPassword = principalPassword;
    }

}
