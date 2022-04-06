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

package io.apicurio.registry.mt.limits;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

/**
 * @author Fabian Martinez
 */
public class MultitenancyLimitsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("registry.enable.multitenancy", "true");

        props.put("registry.limits.config.max-total-schemas", "2");
        props.put("registry.limits.config.max-artifact-properties", "2");
        props.put("registry.limits.config.max-property-key-size", "4"); //use text test
        props.put("registry.limits.config.max-property-value-size", "4");
        props.put("registry.limits.config.max-artifact-labels", "2");
        props.put("registry.limits.config.max-label-size", "4");
        props.put("registry.limits.config.max-name-length", "512");
        props.put("registry.limits.config.max-description-length", "1024");

        //this will do nothing, no server will be available, it's just to test the usage of two decorators at the same time
        props.put("registry.events.sink.testsink", "http://localhost:8888/thisisfailingonpurpose");


        return props;
    }

}