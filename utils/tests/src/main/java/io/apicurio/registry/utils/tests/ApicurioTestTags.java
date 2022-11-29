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

package io.apicurio.registry.utils.tests;

/**
 * @author Fabian Martinez
 */
public class ApicurioTestTags {

    /**Docker is required in the running machine to run this test. */
    public static final String DOCKER = "docker";

    /**Test marked as slow. This usually means that this test uses a profile and therefore an application restart is required. */
    public static final String SLOW = "slow";

}
