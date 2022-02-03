/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rules.compatibility.jsonschema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsonorg.JsonOrgModule;
import io.apicurio.registry.rules.compatibility.jsonschema.diff.DiffContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static io.apicurio.registry.rules.compatibility.jsonschema.JsonSchemaDiffLibrary.findDifferences;

/**
 * @author Jakub Senko 'jsenko@redhat.com'
 */
public class JsonSchemaSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(JsonSchemaSmokeTest.class);

    public static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JsonOrgModule());
    }

    @Test
    public void testComparison() throws Exception {

        Set<String> failed = new HashSet<>(); // TODO add diff

        JSONObject testData = MAPPER.readValue(readResource("compatibility-test-data.json"), JSONObject.class);
        JSONArray testCasesData = testData.getJSONArray("tests");
        for (Object testCaseData_ : testCasesData) {
            JSONObject testCaseData = (JSONObject) testCaseData_;

            String caseId = testCaseData.getString("id");

            if (!testCaseData.getBoolean("enabled")) {
                log.warn("Skipping {}", caseId);
                continue;
            }

            //log.warn("Case: {}", caseId);

            // backward
            DiffContext backward = findDifferences(
                testCaseData.get("original").toString(),
                testCaseData.get("updated").toString());

            // forward
            DiffContext forward = findDifferences(
                testCaseData.get("updated").toString(), // TODO reuse
                testCaseData.get("original").toString());

            boolean backwardCompatible = backward.foundAllDifferencesAreCompatible();
            boolean forwardCompatible = forward.foundAllDifferencesAreCompatible();

            switch (testCaseData.getString("compatibility")) {
                case "backward":
                    if (backwardCompatible && !forwardCompatible) {
                        // ok
                        log.debug("OK caseId: {}", caseId);
                    } else {
                        // bad
                        failed.add(caseId);
                        log.error("\nFailed caseId: {}\nBackward {}: {}\nForward {}: {}\n",
                            caseId, backwardCompatible, backward.getDiff(), forwardCompatible, forward.getDiff());
                    }
                    break;

                case "both":
                    if (backwardCompatible && forwardCompatible) {
                        // ok
                        log.debug("OK caseId: {}", caseId);
                    } else {
                        // bad
                        failed.add(caseId);
                        log.error("\nFailed caseId: {}\nBackward {}: {}\nForward {}: {}\n",
                            caseId, backwardCompatible, backward.getDiff(), forwardCompatible, forward.getDiff());
                    }
                    break;
                case "none":
                    if (!backwardCompatible && !forwardCompatible) {
                        // ok
                        log.debug("OK caseId: {}", caseId);
                    } else {
                        // bad
                        failed.add(caseId);
                        log.error("\nFailed caseId: {}\nBackward {}: {}\nForward {}: {}\n",
                            caseId, backwardCompatible, backward.getDiff(), forwardCompatible, forward.getDiff());
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported compatibility: " + testCaseData.getString("compatibility"));
            }
        }

        if (!failed.isEmpty()) {
            throw new RuntimeException(failed.size() + " test cases failed: " +
                failed.stream().reduce("", (a, s) -> a + "\n" + s));
        }
    }

    private String readResource(String resourceName) {
        try (InputStream stream = getClass().getResourceAsStream(resourceName)) {
            Assertions.assertNotNull(stream, "Resource not found: " + resourceName);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
