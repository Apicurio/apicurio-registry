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

package io.apicurio.registry.rest.client.exception;

import io.apicurio.registry.rest.v2.beans.Error;

/**
 * @author Carles Arnal 'carles.arnal@redhat.com'
 */
public class RoleMappingNotFoundException extends NotFoundException {

    private static final long serialVersionUID = 1L;

    public RoleMappingNotFoundException(Error error) {
        super(error);
    }
}
