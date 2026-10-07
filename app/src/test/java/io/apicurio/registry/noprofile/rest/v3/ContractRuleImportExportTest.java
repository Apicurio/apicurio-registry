/*
 * Copyright 2026 Red Hat
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
package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.rest.client.models.DownloadRef;
import io.apicurio.registry.rest.v3.beans.ContractRule;
import io.apicurio.registry.rest.v3.beans.ContractRuleSet;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static io.restassured.RestAssured.given;

/**
 * Verifies that contract rules survive a full export/import round trip. Contract rules are exported by
 * SqlExportRepository, so an export of any registry containing them used to fail outright because
 * EntityWriter had no case for the ContractRule entity type.
 */
@QuarkusTest
public class ContractRuleImportExportTest extends AbstractResourceTestBase {

    private static final String GROUP_ID = "ContractRuleImportExportTestGroup";
    private static final String ARTIFACT_ID = "ContractRuleImportExportTestArtifact";
    private static final String ARTIFACT_RULE = "artifactScopedRule";
    private static final String VERSION_RULE = "versionScopedRule";
    private static final String GLOBAL_RULE = "globalScopedRule";

    @Inject
    @Current
    RegistryStorage storage;

    @Test
    public void testContractRulesSurviveExportImport() throws Exception {
        storage.deleteAllUserData();

        createArtifact(GROUP_ID, ARTIFACT_ID, ArtifactType.JSON, "{\"type\":\"object\"}",
                ContentTypes.APPLICATION_JSON);

        // An artifact scoped ruleset, a version scoped ruleset and the global ruleset, so that all three
        // shapes of ContractRuleEntity (null globalId, non-null globalId, and __GLOBAL__ coordinates) are
        // covered by the export.
        putRuleSet("/registry/v3/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID + "/contract/ruleset",
                newRuleSet(ARTIFACT_RULE));
        putRuleSet("/registry/v3/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID
                + "/versions/1/contract/ruleset", newRuleSet(VERSION_RULE));
        putRuleSet("/registry/v3/admin/contracts/ruleset", newRuleSet(GLOBAL_RULE));

        // Export. Before the fix this threw "Unhandled entity type: ContractRule" and never produced a zip.
        File exportFile = exportRegistry();

        List<String> contractRuleEntries = zipEntries(exportFile).stream()
                .filter(name -> name.endsWith(".ContractRule.json")).toList();
        Assertions.assertEquals(3, contractRuleEntries.size(),
                "Expected one entry per exported contract rule, found: " + contractRuleEntries);

        // Wipe and re-import.
        storage.deleteAllUserData();
        assertRuleAbsent("/registry/v3/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID
                + "/contract/ruleset");

        importRegistry(exportFile);

        // The reader had no ContractRule case either, so without that half the rules would be silently
        // dropped on the way back in.
        assertSingleRule("/registry/v3/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID
                + "/contract/ruleset", ARTIFACT_RULE);
        assertSingleRule("/registry/v3/groups/" + GROUP_ID + "/artifacts/" + ARTIFACT_ID
                + "/versions/1/contract/ruleset", VERSION_RULE);
        assertSingleRule("/registry/v3/admin/contracts/ruleset", GLOBAL_RULE);

        Files.deleteIfExists(exportFile.toPath());
    }

    private void putRuleSet(String path, ContractRuleSet ruleSet) {
        given().contentType(CT_JSON).body(ruleSet).put(path).then().statusCode(200);
    }

    private void assertSingleRule(String path, String expectedRuleName) {
        ContractRuleSet retrieved = given().get(path).then().statusCode(200).extract()
                .as(ContractRuleSet.class);
        Assertions.assertNotNull(retrieved.getDomainRules(),
                "Expected domain rules to be restored at " + path);
        Assertions.assertEquals(1, retrieved.getDomainRules().size(),
                "Expected exactly one restored rule at " + path);
        ContractRule rule = retrieved.getDomainRules().get(0);
        Assertions.assertEquals(expectedRuleName, rule.getName());
        Assertions.assertEquals("CEL", rule.getType());
        Assertions.assertEquals("true", rule.getExpr());
        Assertions.assertEquals(ContractRule.Kind.CONDITION, rule.getKind());
        Assertions.assertEquals(ContractRule.Mode.WRITE, rule.getMode());
    }

    private void assertRuleAbsent(String path) {
        ContractRuleSet retrieved = given().get(path).then().extract().as(ContractRuleSet.class);
        Assertions.assertTrue(
                retrieved.getDomainRules() == null || retrieved.getDomainRules().isEmpty(),
                "Expected no contract rules after deleting all user data at " + path);
    }

    private File exportRegistry() throws IOException {
        DownloadRef downloadRef = clientV3.admin().export().get();
        URL url = new URL(String.format("http://localhost:%s%s", testPort, downloadRef.getHref()));
        File tempFile = File.createTempFile(ContractRuleImportExportTest.class.getSimpleName(), ".zip");
        try (InputStream in = url.openStream()) {
            Files.deleteIfExists(tempFile.toPath());
            Files.copy(in, tempFile.toPath());
        }
        return tempFile;
    }

    private void importRegistry(File exportFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(exportFile)) {
            clientV3.admin().importEscaped().post(fis, config -> {
                config.headers.putIfAbsent("Content-Type", Set.of("application/zip"));
            });
        }
    }

    private List<String> zipEntries(File file) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }

    private ContractRuleSet newRuleSet(String ruleName) {
        ContractRule rule = new ContractRule();
        rule.setName(ruleName);
        rule.setKind(ContractRule.Kind.CONDITION);
        rule.setType("CEL");
        rule.setMode(ContractRule.Mode.WRITE);
        rule.setExpr("true");

        ContractRuleSet ruleSet = new ContractRuleSet();
        ruleSet.setDomainRules(List.of(rule));
        ruleSet.setMigrationRules(List.of());
        return ruleSet;
    }
}
