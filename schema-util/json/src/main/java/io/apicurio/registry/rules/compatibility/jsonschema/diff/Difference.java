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

package io.apicurio.registry.rules.compatibility.jsonschema.diff;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
@Builder
@Getter
@EqualsAndHashCode
@ToString
public class Difference {

    @NonNull
    private final DiffType diffType;

    @NonNull
    private final String pathOriginal;

    @NonNull
    private final String pathUpdated;

    @NonNull
    private final String subSchemaOriginal;

    @NonNull
    private final String subSchemaUpdated;
}
