/*
 * Copyright 2021 Red Hat Inc
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

package io.apicurio.registry.ccompat.store;

import java.util.function.Supplier;

import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.common.apps.config.Dynamic;
import io.apicurio.common.apps.config.Info;

/**
 * @author eric.wittmann@gmail.com
 */
@Singleton
public class CCompatConfig {

    @Dynamic(label = "Legacy ID mode (compatibility API)", description =  "When selected, the Schema Registry compatibility API uses global ID instead of content ID for artifact identifiers.")
    @ConfigProperty(name = "registry.ccompat.legacy-id-mode.enabled", defaultValue = "false")
    @Info(category = "ccompat", description = "Legacy ID mode (compatibility API)", availableSince = "2.0.2.Final")
    Supplier<Boolean> legacyIdModeEnabled;

    @Dynamic(label = "Canonical hash mode (compatibility API)", description = "When selected, the Schema Registy compatibility API uses the canonical hash instead of the regular hash of the content.")
    @ConfigProperty(name = "registry.ccompat.use-canonical-hash", defaultValue = "false")
    @Info(category = "ccompat", description = "Canonical hash mode (compatibility API)", availableSince = "2.3.0.Final")
    Supplier<Boolean> canonicalHashModeEnabled;
}
