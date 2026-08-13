package io.apicurio.registry.search;

import java.util.HashMap;
import java.util.Map;

/**
 * Test profile that enables the Elasticsearch search index (via Dev Services) together with A2A
 * protocol support, so that well-known agent searches with skill/capability/mode filters can be
 * served by the search index.
 */
public class ElasticsearchA2ATestProfile extends ElasticsearchSearchTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>(super.getConfigOverrides());
        overrides.put("apicurio.a2a.enabled", "true");
        return overrides;
    }
}
