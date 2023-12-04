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

package io.apicurio.registry.storage.error;

import lombok.Getter;


public class RoleMappingNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -2662972482016902671L;

    @Getter
    private String principalId;

    @Getter
    private String role;


    public RoleMappingNotFoundException(String principalId) {
        super("No role mapping for principal '" + principalId + "' was found.");
        this.principalId = principalId;
    }


    public RoleMappingNotFoundException(String principalId, String role) {
        super("No mapping for principal '" + principalId + "' and role '" + role + "' was found.");
        this.principalId = principalId;
        this.role = role;
    }
}
