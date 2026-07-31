package io.apicurio.registry.noprofile.rest.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.v3.beans.ContractRule;
import io.apicurio.registry.rest.v3.beans.ContractRuleSet;
import io.apicurio.registry.rest.v3.impl.shared.DataExporter;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.BranchMetaData;
import io.apicurio.registry.rest.client.models.BranchSearchResults;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateBranch;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.DownloadRef;
import io.apicurio.registry.rest.client.models.EditableArtifactMetaData;
import io.apicurio.registry.rest.client.models.EditableVersionMetaData;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.GroupSortBy;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.rest.client.models.Rule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rules.integrity.IntegrityLevel;
import io.apicurio.registry.rules.validity.ValidityLevel;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.cdi.Current;
import io.apicurio.registry.utils.impexp.v3.ContractRuleEntity;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.StreamingOutput;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
public class ImportExportTest extends AbstractResourceTestBase {

    private static final Logger log = LoggerFactory.getLogger(ImportExportTest.class);

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    DataExporter exporter;

    @Test
    public void testExportImport() throws Exception {
        /**
         * Delete all the data.
         */
        storage.deleteAllUserData();

        /**
         * Populate with new data.
         */

        String groupId = "PrimaryTestGroup";

        // Create the group
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        createGroup.setDescription("The group for the export/import test.");
        createGroup.setLabels(new Labels());
        createGroup.getLabels().setAdditionalData(Map.of("isPrimary", "true"));
        clientV3.groups().post(createGroup);

        // Create another group
        createGroup = new CreateGroup();
        createGroup.setGroupId("SecondaryTestGroup");
        createGroup.setDescription("Another test group that is empty.");
        createGroup.setLabels(new Labels());
        createGroup.getLabels().setAdditionalData(Map.of("isPrimary", "false"));
        clientV3.groups().post(createGroup);

        String secondaryGroupId = "SecondaryTestGroup";

        // Configure a group rule
        CreateRule createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.ALL_REFS_MAPPED.name());
        clientV3.groups().byGroupId("SecondaryTestGroup").rules().post(createRule);

        // Add an empty artifact
        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId("EmptyArtifact");
        createArtifact.setArtifactType(ArtifactType.JSON);
        createArtifact.setName("Empty artifact");
        createArtifact.setDescription("Empty artifact description");
        clientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Add artifacts
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = "TestArtifact-" + idx;
            createArtifact(groupId, artifactId, ArtifactType.JSON, "{}", ContentTypes.APPLICATION_JSON);
        }

        String secondaryArtifactId = "SecondaryArtifact";
        createArtifact(secondaryGroupId, secondaryArtifactId, ArtifactType.JSON, "{}",
                ContentTypes.APPLICATION_JSON);

        // Set artifact metadata
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = "TestArtifact-" + idx;
            EditableArtifactMetaData metaData = new EditableArtifactMetaData();
            metaData.setName("Artifact #" + idx);
            metaData.setDescription("This is artifact number: " + idx);
            metaData.setLabels(new Labels());
            metaData.getLabels().setAdditionalData(Map.of("artifact-number", "" + idx));
            clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).put(metaData);
        }

        // Add versions to the artifacts
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = "TestArtifact-" + idx;
            int numExtraVersions = idx - 1;
            for (int jdx = 0; jdx < numExtraVersions; jdx++) {
                createArtifactVersion(groupId, artifactId, "{\"title\": \"Version " + jdx + "\"}",
                        ContentTypes.APPLICATION_JSON);
            }
        }

        // Set version metadata
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = "TestArtifact-" + idx;
            for (int jdx = 0; jdx < idx; jdx++) {
                EditableVersionMetaData metaData = new EditableVersionMetaData();
                metaData.setName("Version #" + jdx);
                metaData.setDescription("This is version number: " + jdx);
                metaData.setLabels(new Labels());
                metaData.getLabels()
                        .setAdditionalData(Map.of("artifact-number", "" + idx, "version-number", "" + jdx));
                clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                        .byVersionExpression("" + (jdx + 1)).put(metaData);
            }
        }

        // Add some branches
        String artifactIdWithBranches = "TestArtifact-5";

        CreateBranch createBranch = new CreateBranch();
        createBranch.setBranchId("odds");
        createBranch.setDescription("Odd numbered versions");
        createBranch.setVersions(List.of("1", "3", "5"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithBranches).branches()
                .post(createBranch);
        createBranch = new CreateBranch();
        createBranch.setBranchId("evens");
        createBranch.setDescription("Even numbered versions");
        createBranch.setVersions(List.of("2", "4"));
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithBranches).branches()
                .post(createBranch);

        // Configure some global rules
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.FULL.name());
        clientV3.admin().rules().post(createRule);

        // Configure some artifact rules
        String artifactIdWithRules = "TestArtifact-7";

        createRule = new CreateRule();
        createRule.setRuleType(RuleType.VALIDITY);
        createRule.setConfig(ValidityLevel.SYNTAX_ONLY.name());
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithRules).rules()
                .post(createRule);
        createRule = new CreateRule();
        createRule.setRuleType(RuleType.INTEGRITY);
        createRule.setConfig(IntegrityLevel.FULL.name());
        clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithRules).rules()
                .post(createRule);

        String artifactIdWithContractRules = "TestArtifact-8";
        setContractRules("/registry/v3/admin/contracts/ruleset", "global-contract-rule");
        setContractRules(
                "/registry/v3/groups/" + groupId + "/artifacts/" + artifactIdWithContractRules
                        + "/contract/ruleset",
                "artifact-contract-rule");
        setContractRules(
                "/registry/v3/groups/" + groupId + "/artifacts/" + artifactIdWithContractRules
                        + "/versions/1/contract/ruleset",
                "version-contract-rule");
        setContractRules(
                "/registry/v3/groups/" + secondaryGroupId + "/artifacts/" + secondaryArtifactId
                        + "/contract/ruleset",
                "secondary-contract-rule");

        Long originalVersionGlobalId = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactIdWithContractRules).versions().byVersionExpression("1").get()
                .getGlobalId();

        DownloadRef groupDownloadRef = clientV3.admin().export().get(config -> {
            config.queryParameters.groupId = groupId;
        });
        File groupExport = File.createTempFile(ImportExportTest.class.getSimpleName(), ".zip");
        downloadTo(new URL(String.format("http://localhost:%s%s", testPort, groupDownloadRef.getHref())),
                groupExport);
        List<ContractRuleEntity> groupContractRules = readContractRules(groupExport);
        Assertions.assertEquals(2, groupContractRules.size());
        Assertions.assertEquals(Set.of("artifact-contract-rule", "version-contract-rule"),
                groupContractRules.stream().map(ruleEntity -> ruleEntity.ruleName)
                        .collect(Collectors.toSet()));
        Files.delete(groupExport.toPath());

        // Add some comments
        // TODO add comments

        /**
         * Export the data!
         */

        DownloadRef downloadRef = clientV3.admin().export().get();
        String href = downloadRef.getHref();
        String urlString = String.format("http://localhost:%s%s", testPort, href);
        URL url = new URL(urlString);
        // TODO save to a file in the 'target' directory?
        File tempFile = File.createTempFile(ImportExportTest.class.getSimpleName(), ".zip");
        System.out.println("Temp export file: " + tempFile.toPath());
        downloadTo(url, tempFile);
        listFiles(tempFile);
        assertContractRuleEntries(tempFile, 4);

        /**
         * Delete all the data.
         */
        storage.deleteAllUserData();

        /**
         * Import the data back into the (now empty) registry
         */
        importData(tempFile, true);

        /**
         * Assertions!
         */

        // Assert groups
        GroupSearchResults groups = clientV3.groups().get(config -> {
            config.queryParameters.orderby = GroupSortBy.GroupId;
        });
        Assertions.assertEquals(2, groups.getCount());
        Assertions.assertEquals("PrimaryTestGroup", groups.getGroups().get(0).getGroupId());
        Assertions.assertEquals("The group for the export/import test.",
                groups.getGroups().get(0).getDescription());
        Assertions.assertEquals("SecondaryTestGroup", groups.getGroups().get(1).getGroupId());
        Assertions.assertEquals("Another test group that is empty.",
                groups.getGroups().get(1).getDescription());

        // TODO: check group labels (not returned by group search)

        // Assert group rules
        List<RuleType> groupRules = clientV3.groups().byGroupId("SecondaryTestGroup").rules().get();
        Assertions.assertEquals(1, groupRules.size());
        Assertions.assertEquals(List.of(RuleType.INTEGRITY), groupRules);
        Rule groupRule = clientV3.groups().byGroupId("SecondaryTestGroup").rules()
                .byRuleType(RuleType.INTEGRITY.name()).get();
        Assertions.assertEquals(IntegrityLevel.ALL_REFS_MAPPED.name(), groupRule.getConfig());

        // Assert empty artifact
        ArtifactMetaData amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId("EmptyArtifact")
                .get();
        Assertions.assertEquals(groupId, amd.getGroupId());
        Assertions.assertEquals("EmptyArtifact", amd.getArtifactId());
        Assertions.assertEquals("Empty artifact", amd.getName());
        Assertions.assertEquals("Empty artifact description", amd.getDescription());
        Assertions.assertEquals(ArtifactType.JSON, amd.getArtifactType());

        // Assert artifacts
        ArtifactSearchResults artifacts = clientV3.groups().byGroupId(groupId).artifacts().get(config -> {
            config.queryParameters.orderby = ArtifactSortBy.ArtifactId;
        });
        Assertions.assertEquals(11, artifacts.getCount());
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = "TestArtifact-" + idx;
            amd = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).get();
            Assertions.assertEquals(artifactId, amd.getArtifactId());
            Assertions.assertEquals("Artifact #" + idx, amd.getName());
            Assertions.assertEquals("This is artifact number: " + idx, amd.getDescription());
            Assertions.assertEquals(Map.of("artifact-number", "" + idx), amd.getLabels().getAdditionalData());
            Assertions.assertEquals(ArtifactType.JSON, amd.getArtifactType());
        }

        // Assert versions
        for (int idx = 1; idx <= 10; idx++) {
            String artifactId = "TestArtifact-" + idx;
            int expectedNumberOfVersions = idx;

            // Check the artifact has the correct # of versions
            VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions().get();
            Assertions.assertEquals(expectedNumberOfVersions, versions.getCount());

            // Check each version
            for (int jdx = 0; jdx < idx; jdx++) {
                String version = "" + (jdx + 1);
                VersionMetaData vmd = clientV3.groups().byGroupId(groupId).artifacts()
                        .byArtifactId(artifactId).versions().byVersionExpression(version).get();
                Assertions.assertEquals(groupId, vmd.getGroupId());
                Assertions.assertEquals(artifactId, vmd.getArtifactId());
                Assertions.assertEquals(version, vmd.getVersion());
                Assertions.assertEquals("Version #" + jdx, vmd.getName());
                Assertions.assertEquals("This is version number: " + jdx, vmd.getDescription());
            }
        }

        // Assert branches
        BranchSearchResults branches = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactIdWithBranches).branches().get();
        Assertions.assertEquals(3, branches.getCount());
        Assertions.assertEquals("evens", branches.getBranches().get(0).getBranchId());
        Assertions.assertEquals("latest", branches.getBranches().get(1).getBranchId());
        Assertions.assertEquals("odds", branches.getBranches().get(2).getBranchId());

        BranchMetaData branch = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactIdWithBranches).branches().byBranchId("evens").get();
        Assertions.assertEquals("evens", branch.getBranchId());
        Assertions.assertEquals("Even numbered versions", branch.getDescription());
        Assertions.assertEquals(false, branch.getSystemDefined());
        branch = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithBranches)
                .branches().byBranchId("latest").get();
        Assertions.assertEquals(true, branch.getSystemDefined());

        VersionSearchResults versions = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactIdWithBranches).branches().byBranchId("evens").versions().get();
        Assertions.assertEquals(2, versions.getCount());
        versions = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithBranches)
                .branches().byBranchId("odds").versions().get();
        Assertions.assertEquals(3, versions.getCount());

        // Assert global rules
        List<RuleType> rules = clientV3.admin().rules().get();
        Assertions.assertEquals(1, rules.size());
        Rule rule = clientV3.admin().rules().byRuleType(RuleType.VALIDITY.name()).get();
        Assertions.assertEquals(ValidityLevel.FULL.name(), rule.getConfig());

        // Assert artifact rules
        rules = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithRules).rules()
                .get();
        Assertions.assertEquals(2, rules.size());
        rule = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithRules).rules()
                .byRuleType(RuleType.VALIDITY.name()).get();
        Assertions.assertEquals(ValidityLevel.SYNTAX_ONLY.name(), rule.getConfig());
        rule = clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactIdWithRules).rules()
                .byRuleType(RuleType.INTEGRITY.name()).get();
        Assertions.assertEquals(IntegrityLevel.FULL.name(), rule.getConfig());

        assertContractRules(groupId, artifactIdWithContractRules);
        Long preservedVersionGlobalId = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactIdWithContractRules).versions().byVersionExpression("1").get()
                .getGlobalId();
        Assertions.assertEquals(originalVersionGlobalId, preservedVersionGlobalId);

        storage.deleteAllUserData();
        storage.nextGlobalId();
        importData(tempFile, false);

        Long remappedVersionGlobalId = clientV3.groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactIdWithContractRules).versions().byVersionExpression("1").get()
                .getGlobalId();
        Assertions.assertNotEquals(originalVersionGlobalId, remappedVersionGlobalId);
        assertContractRules(groupId, artifactIdWithContractRules);

        Files.delete(tempFile.toPath());

        // Assert artifact comments
        // TODO add comments

    }

    @Test
    public void testExportFailsWhenAnEntityCannotBeWritten() {
        StreamingOutput stream = (StreamingOutput) exporter.exportData().getEntity();

        IOException exception = Assertions.assertThrows(IOException.class,
                () -> stream.write(new OutputStream() {
                    @Override
                    public void write(int value) throws IOException {
                        throw new IOException("Output stream failure");
                    }
                }));

        Assertions.assertEquals("Error writing entity", exception.getCause().getMessage());
    }

    private static void setContractRules(String path, String ruleName) {
        ContractRule contractRule = new ContractRule();
        contractRule.setName(ruleName);
        contractRule.setKind(ContractRule.Kind.CONDITION);
        contractRule.setType("CEL");
        contractRule.setMode(ContractRule.Mode.WRITE);
        contractRule.setExpr("true");
        contractRule.setOnFailure(ContractRule.OnFailure.ERROR);

        ContractRuleSet ruleset = new ContractRuleSet();
        ruleset.setDomainRules(List.of(contractRule));
        ruleset.setMigrationRules(List.of());

        given().contentType("application/json").body(ruleset).put(path).then().statusCode(200);
    }

    private static void assertContractRules(String groupId, String artifactId) {
        given().get("/registry/v3/admin/contracts/ruleset").then().statusCode(200)
                .body("domainRules[0].name", equalTo("global-contract-rule"));
        given().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/contract/ruleset").then()
                .statusCode(200).body("domainRules[0].name", equalTo("artifact-contract-rule"));
        given().pathParam("groupId", groupId).pathParam("artifactId", artifactId)
                .get("/registry/v3/groups/{groupId}/artifacts/{artifactId}/versions/1/contract/ruleset")
                .then().statusCode(200).body("domainRules[0].name", equalTo("version-contract-rule"));
    }

    private void importData(File export, boolean preserveGlobalId) throws IOException {
        try (FileInputStream fis = new FileInputStream(export)) {
            clientV3.admin().importEscaped().post(fis, config -> {
                config.headers.putIfAbsent("Content-Type", Set.of("application/zip"));
                config.headers.putIfAbsent("X-Registry-Preserve-GlobalId",
                        Set.of(Boolean.toString(preserveGlobalId)));
            });
        }
    }

    private static void assertContractRuleEntries(File export, int expectedCount) throws IOException {
        try (ZipFile zipFile = new ZipFile(export)) {
            List<String> entryNames = zipFile.stream().map(ZipEntry::getName)
                    .filter(name -> name.endsWith(".ContractRule.json")).toList();
            Assertions.assertEquals(expectedCount, entryNames.size());
            Assertions.assertTrue(entryNames.stream()
                    .allMatch(name -> name.matches("contractRules/\\d+\\.ContractRule\\.json")));
        }
    }

    private static List<ContractRuleEntity> readContractRules(File export) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (ZipFile zipFile = new ZipFile(export)) {
            return zipFile.stream().filter(entry -> entry.getName().endsWith(".ContractRule.json"))
                    .map(entry -> {
                        try (InputStream input = zipFile.getInputStream(entry)) {
                            return mapper.readValue(input, ContractRuleEntity.class);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }).toList();
        }
    }

    private static void downloadTo(URL url, File tempFile) {
        try (InputStream in = url.openStream()) {
            Files.deleteIfExists(tempFile.toPath());
            Files.copy(in, tempFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void listFiles(File tempFile) {
        log.debug("--- Export ZIP File Listing ---");
        try (ZipFile zipFile = new ZipFile(tempFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                log.debug(entry.getName());
            }
        } catch (IOException e) {
            log.warn("Failed to list ZIP file entries", e);
        }
        log.debug("--- Export ZIP File Listing ---");
    }

}
