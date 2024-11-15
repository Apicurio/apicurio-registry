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

import java.util.Map;
import java.util.Optional;

/**
 * Base class for all storage exceptions.
 *
 * @author eric.wittmann@gmail.com
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public class StorageException extends Exception {

    private static final long serialVersionUID = 7551763806044016474L;

    @Getter
    protected Optional<Map<String, String>> context = Optional.empty();

    public StorageException(String reason, Map<String, String> context) {
        super(reason);
        this.context = Optional.ofNullable(context);
    }

    public StorageException(Map<String, String> context, Throwable cause) {
        super(cause);
        this.context = Optional.ofNullable(context);
    }

    public StorageException(String reason, Map<String, String> context, Throwable cause) {
        super(reason, cause);
        this.context = Optional.ofNullable(context);
    }

    public boolean isRoot() {
        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": " + Optional.ofNullable(super.toString()).orElse("<no reason>")
                + context.filter(m -> !m.isEmpty()).map(c -> " with context " + c).orElse(" with no context");
    }
}
