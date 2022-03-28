/*
 * Copyright 2022 Red Hat
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

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author carnalca@redhat.com
 */
public class AuthTestProfileAuthenticatedReadAccess implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("registry.auth.authenticated-read-access.enabled", "true", "smallrye.jwt.sign.key.location", "privateKey.jwk");
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Collections.singletonList(
                new TestResourceEntry(JWKSMockServer.class));
    }
}
