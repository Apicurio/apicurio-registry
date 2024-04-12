package io.apicurio.registry.rest;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTestProfile;

public class MultipleRequestFiltersTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> props = new HashMap<>();
        props.put("apicurio.disable.apis", "/apis/ccompat/v7/subjects/[^/]+/versions.*,/ui/.*");
        return props;
    }

}