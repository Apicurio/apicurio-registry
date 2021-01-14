/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.storage.impl.kafkasql.keys;

/**
 * Base class for all message keys.
 * @author eric.wittmann@gmail.com
 */
public abstract class AbstractMessageKey implements MessageKey {
    
    private String tenantId;

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.keys.MessageKey#getTenantId()
     */
    @Override
    public String getTenantId() {
        return tenantId;
    }

    /**
     * @param tenantId the tenantId to set
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public abstract String toString();

}
