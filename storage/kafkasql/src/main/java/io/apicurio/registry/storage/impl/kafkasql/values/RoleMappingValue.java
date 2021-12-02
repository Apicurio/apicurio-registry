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

package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
@ToString
public class RoleMappingValue extends AbstractMessageValue {

    private String role;
    private String principalName;

    /**
     * Creator method.
     * @param action
     * @param role
     * @param principalName
     */
    public static final RoleMappingValue create(ActionType action, String role, String principalName) {
        RoleMappingValue value = new RoleMappingValue();
        value.setAction(action);
        value.setRole(role);
        value.setPrincipalName(principalName);
        return value;
    }

    /**
     * @see io.apicurio.registry.storage.impl.kafkasql.values.MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.RoleMapping;
    }

    /**
     * @return the role
     */
    public String getRole() {
        return role;
    }

    /**
     * @param role the role to set
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * @return the principalName
     */
    public String getPrincipalName() {
        return principalName;
    }

    /**
     * @param principalName the principalName to set
     */
    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
    }

}
