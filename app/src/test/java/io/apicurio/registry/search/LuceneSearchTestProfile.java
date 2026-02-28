package io.apicurio.registry.search;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Test profile that enables Lucene search indexing with synchronous updates. Used by integration tests
 * that verify version searches are routed through the Lucene index instead of SQL.
 */
public class LuceneSearchTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        String indexPath = resolveIndexPath("apicurio-lucene-test");
        return Map.of(
                "apicurio.search.lucene.enabled", "true",
                "apicurio.search.lucene.update-mode", "SYNCHRONOUS",
                "apicurio.search.lucene.index-path", indexPath
        );
    }

    /**
     * Resolves the Lucene index directory path for tests. Prefers locations that are cleaned
     * by {@code mvn clean}, falling back to the system temp directory for IDE-based runs.
     *
     * <ol>
     *   <li>{@code project.build.directory} system property (set via Surefire config in pom.xml)</li>
     *   <li>{@code ./target} resolved from the working directory</li>
     *   <li>{@code java.io.tmpdir} as a last resort</li>
     * </ol>
     *
     * @param prefix a descriptive prefix for the index directory name
     * @return the absolute path to use for the Lucene index
     */
    static String resolveIndexPath(String prefix) {
        String dirName = prefix + "-" + ProcessHandle.current().pid() + "-" + System.nanoTime();
        String buildDir = System.getProperty("project.build.directory");
        if (buildDir != null) {
            return buildDir + "/" + dirName;
        }
        Path targetDir = Path.of("target").toAbsolutePath();
        if (Files.isDirectory(targetDir)) {
            return targetDir.resolve(dirName).toString();
        }
        return System.getProperty("java.io.tmpdir") + "/" + dirName;
    }
}
