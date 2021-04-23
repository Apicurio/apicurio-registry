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

package io.apicurio.registry.events;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.apicurio.registry.test.utils.KafkaTestContainerManager;
import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * @author Fabian Martinez
 */
public class KafkaEventsProfile implements QuarkusTestProfile {

    public static final String EVENTS_TOPIC = "registry-events";

    @Override
    public Map<String, String> getConfigOverrides() {
        return Collections.emptyMap();
    }

    @Override
    public List<TestResourceEntry> testResources() {
        return Arrays.asList(
                new TestResourceEntry(KafkaEventsTestResource.class),
                new TestResourceEntry(KafkaTestContainerManager.class));
    }

}
