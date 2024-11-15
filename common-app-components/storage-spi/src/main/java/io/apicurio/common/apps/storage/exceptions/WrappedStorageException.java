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

package io.apicurio.common.apps.storage.exceptions;

import lombok.Getter;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class WrappedStorageException extends RuntimeException {

    private static final long serialVersionUID = 8503794682205122835L;

    @Getter
    private final StorageException wrapped;

    public WrappedStorageException(StorageException ex) {
        wrapped = ex;
    }

    @Override
    public String getMessage() {
        return wrapped.getMessage();
    }

    @Override
    public synchronized Throwable getCause() {
        return wrapped.getCause();
    }
}
