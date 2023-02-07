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

package io.apicurio.registry.rules.compatibility;

import io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.Difference;

import java.util.Set;

/**
 * @author Ales Justin
 * @author Jonathan Halliday
 * @author Jakub Senko <m@jsenko.net>
 */
public class JsonSchemaCompatibilityChecker extends AbstractCompatibilityChecker<Difference> {

    @Override
    protected Set<Difference> isBackwardsCompatibleWith(String existing, String proposed) {
        return JsonSchemaDiffLibrary.getIncompatibleDifferences(existing, proposed);
    }

    @Override
    protected CompatibilityDifference transform(Difference original) {
        return new JsonSchemaCompatibilityDifference(original);
    }
}
