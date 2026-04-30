/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.content.canon;

/**
 * Exception thrown when an error occurs during content canonicalization.
 * This exception provides consistent error handling across all ContentCanonicalizer implementations.
 */
public class ContentCanonicalizationException extends Exception {

    private static final long serialVersionUID = 1L;

    public ContentCanonicalizationException() {
        super();
    }

    public ContentCanonicalizationException(String reason) {
        super(reason);
    }

    public ContentCanonicalizationException(Throwable cause) {
        super(cause);
    }

    public ContentCanonicalizationException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
