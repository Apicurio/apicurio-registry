package io.apicurio.registry.search;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that enables Lucene search indexing with asynchronous (polling-based) updates.
 * Used by integration tests that verify the async indexing pipeline picks up version changes
 * and makes them searchable via the Lucene index.
 */
public class AsyncLuceneSearchTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        String indexPath = System.getProperty("java.io.tmpdir") + "/apicurio-lucene-async-test-"
                + ProcessHandle.current().pid() + "-" + System.nanoTime();
        return Map.of(
                "apicurio.search.lucene.enabled", "true",
                "apicurio.search.lucene.update-mode", "ASYNCHRONOUS",
                "apicurio.search.lucene.index-path", indexPath,
                "apicurio.search.lucene.polling-interval", "3s",
                "apicurio.search.lucene.polling-initial-delay", "1s"
        );
    }
}
