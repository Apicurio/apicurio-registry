/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.storage;

/**
 * @author eric.wittmann@gmail.com
 */
public class RegistryStorageException extends StorageException {

    private static final long serialVersionUID = 708084955101638005L;
    
    /**
     * Constructor.
     * @param cause
     */
    public RegistryStorageException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     * @param reason
     * @param cause
     */
    public RegistryStorageException(String reason, Throwable cause) {
        super(reason, cause);
    }

    /**
     * Constructor.
     * @param reason
     */
    public RegistryStorageException(String reason) {
        super(reason);
    }

}
