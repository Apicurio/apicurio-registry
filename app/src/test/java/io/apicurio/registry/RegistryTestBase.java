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
package io.apicurio.registry;

import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import io.apicurio.registry.util.ServiceInitializer;

/**
 * Base class for all registry tests.
 *
 * @author Fabian Martinez
 */
@TestInstance(Lifecycle.PER_CLASS)
public abstract class RegistryTestBase extends AbstractRegistryTestBase {

    @Inject
    Instance<ServiceInitializer> initializers;

    @BeforeEach
    protected void beforeEach() throws Exception {
        prepareServiceInitializers();
    }

    protected void prepareServiceInitializers() {
        // run all initializers::beforeEach
        initializers.stream().forEach(ServiceInitializer::beforeEach);
    }

}
