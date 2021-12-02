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

package io.apicurio.registry.ui.beans;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * @author eric.wittmann@gmail.com
 */
@RegisterForReflection
public class ConfigJs {

    public ConfigJsArtifacts artifacts = new ConfigJsArtifacts();
    public ConfigJsUi ui = new ConfigJsUi();
    public ConfigJsFeatures features = new ConfigJsFeatures();
    public ConfigJsAuth auth = new ConfigJsAuth();

}
