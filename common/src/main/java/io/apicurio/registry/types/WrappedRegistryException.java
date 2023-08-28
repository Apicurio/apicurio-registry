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

package io.apicurio.registry.types;

import lombok.Getter;

/**
 * This exception is used to wrap a checked Registry exception into an unchecked one.
 * <p>
 * This is used, for example, to throw {@see io.apicurio.registry.storage.error.CheckedRegistryStorageException}
 * from the REST API methods, until the codegen supports checked exceptions.
 * We can't use @SneakyThrows(ReadOnlyStorageException.class) because Arc will throw
 * an {@see io.quarkus.arc.ArcUndeclaredThrowableException}.
 *
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class WrappedRegistryException extends RuntimeException {

    private static final long serialVersionUID = -6439379373254391061L;

    @Getter
    private CheckedRegistryException wrapped;

    private WrappedRegistryException(CheckedRegistryException wrapped) {
        super(wrapped);
        this.wrapped = wrapped;
    }

    public static WrappedRegistryException wrap(CheckedRegistryException ex) {
        return new WrappedRegistryException(ex);
    }
}
