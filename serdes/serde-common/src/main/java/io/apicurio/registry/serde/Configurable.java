package io.apicurio.registry.serde;

import java.util.Map;

public interface Configurable {


    void configure(Map<String, Object> configs);
}
