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

import io.apicurio.registry.types.CheckedRegistryException;

/**
 * This class is intended for extension. Create a more specific exception.
 *
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public abstract class CheckedRegistryStorageException extends CheckedRegistryException {

    private static final long serialVersionUID = 2845053914080597135L;


    protected CheckedRegistryStorageException(String reason) {
        super(reason);
    }


    protected CheckedRegistryStorageException(Throwable cause) {
        super(cause);
    }


    protected CheckedRegistryStorageException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
