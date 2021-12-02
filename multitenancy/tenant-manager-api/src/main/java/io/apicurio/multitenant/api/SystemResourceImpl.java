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

package io.apicurio.multitenant.api;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.apicurio.multitenant.api.datamodel.SystemInfo;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class SystemResourceImpl implements SystemResource {

    @Inject
    TenantManagerSystem system;

    /**
     * @see io.apicurio.multitenant.api.SystemResource#getSystemInfo()
     */
    @Override
    public SystemInfo getSystemInfo() {
        SystemInfo info = new SystemInfo();
        info.setName(system.getName());
        info.setDescription(system.getDescription());
        info.setVersion(system.getVersion());
        info.setBuiltOn(system.getDate());
        return info;
    }

}
