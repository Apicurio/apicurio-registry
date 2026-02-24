package io.apicurio.registry.search;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables Lucene search indexing with synchronous updates. Used by integration tests
 * that verify version searches are routed through the Lucene index instead of SQL.
 */
public class LuceneSearchTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        String indexPath = System.getProperty("java.io.tmpdir") + "/apicurio-lucene-test-"
                + ProcessHandle.current().pid() + "-" + System.nanoTime();
        return Map.of(
                "apicurio.search.lucene.enabled", "true",
                "apicurio.search.lucene.update-mode", "SYNCHRONOUS",
                "apicurio.search.lucene.index-path", indexPath
        );
    }
}
