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

package io.apicurio.tests.common;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.tests.common.interfaces.TestSeparator;

/**
 * Base class for all base classes for integration tests or for integration tests directly.
 * This class must not contain any functionality nor implement any beforeAll, beforeeEach,...
 * the idea is to have this class only to register {@link RegistryDeploymentManager} in only one place.
 *
 * @author Fabian Martinez
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@ExtendWith(RegistryDeploymentManager.class)
@TestInstance(Lifecycle.PER_CLASS)
public class ApicurioRegistryBaseIT implements TestSeparator, Constants {

}
