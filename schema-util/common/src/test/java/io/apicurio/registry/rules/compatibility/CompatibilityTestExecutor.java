package io.apicurio.registry.rules.compatibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CompatibilityTestExecutor {

    private static final Logger log = LoggerFactory.getLogger(CompatibilityTestExecutor.class);

    public static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JsonOrgModule());
    }

    private CompatibilityChecker checker;

    public CompatibilityTestExecutor(CompatibilityChecker checker) {
        this.checker = checker;
    }

    public Set<String> execute(String testData) throws Exception {

        Set<String> failed = new HashSet<>(); // TODO add diff

        JSONObject testDataJson = MAPPER.readValue(testData, JSONObject.class);
        JSONArray testCasesData = testDataJson.getJSONArray("tests");
        for (Object testCaseData_ : testCasesData) {
            JSONObject testCaseData = (JSONObject) testCaseData_;

            String caseId = testCaseData.getString("id");

            if (!testCaseData.getBoolean("enabled")) {
                log.warn("Skipping {}", caseId);
                continue;
            }

            log.info("Running test case: {}", caseId);
            var original = testCaseData.get("original").toString();
            var updated = testCaseData.get("updated").toString();

            var resultBackward = checker.testCompatibility(CompatibilityLevel.BACKWARD, List.of(original),
                    updated, Collections.emptyMap());
            var resultForward = checker.testCompatibility(CompatibilityLevel.FORWARD, List.of(original),
                    updated, Collections.emptyMap());

            switch (testCaseData.getString("compatibility")) {
                case "backward":
                    if (resultBackward.isCompatible() && !resultForward.isCompatible()) {
                        // ok
                        log.debug("OK caseId: {}", caseId);
                    } else {
                        // bad
                        failed.add(caseId);
                        logFail(caseId, resultBackward, resultForward);
                    }
                    break;

                case "both":
                    if (resultBackward.isCompatible() && resultForward.isCompatible()) {
                        // ok
                        log.debug("OK caseId: {}", caseId);
                    } else {
                        // bad
                        failed.add(caseId);
                        logFail(caseId, resultBackward, resultForward);
                    }
                    break;
                case "none":
                    if (!resultBackward.isCompatible() && !resultForward.isCompatible()) {
                        // ok
                        log.debug("OK caseId: {}", caseId);
                    } else {
                        // bad
                        failed.add(caseId);
                        logFail(caseId, resultBackward, resultForward);
                    }
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported compatibility type: " + testCaseData.getString("compatibility"));
            }
        }
        return failed;
    }

    public static void throwOnFailure(Set<String> failed) {
        if (!failed.isEmpty()) {
            throw new RuntimeException(failed.size() + " test cases failed: "
                    + failed.stream().reduce("", (a, s) -> a + "\n" + s));
        }
    }

    private static void logFail(String caseId, CompatibilityExecutionResult resultBackward,
            CompatibilityExecutionResult resultForward) {
        log.error("\nFailed caseId: {}\nBackward {}: {}\nForward {}: {}\n", caseId,
                resultBackward.isCompatible(), resultBackward.getIncompatibleDifferences(),
                resultForward.isCompatible(), resultForward.getIncompatibleDifferences());
    }

    public static String readResource(Class<?> localClass, String resourceName) {
        try (InputStream stream = localClass.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
