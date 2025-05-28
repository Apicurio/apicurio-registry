package io.apicurio.registry.storage.impl.readonly;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.config.DynamicConfigStorage;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.Functional.Runnable1Ex;
import io.apicurio.registry.utils.tests.ApicurioTestTags;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@Tag(ApicurioTestTags.SLOW)
public class ReadOnlyRegistryStorageTest {

    @Inject
    @Current
    RegistryStorage storage;

    private static final Map<String, State> EXPECTED_METHODS;
    private static final Set<String> CURRENT_METHODS;

    static {
        EXPECTED_METHODS = Map.<String, State> ofEntries(
                // Keep alphabetical
                entry("appendVersionToBranch3",
                        new State(true, s -> s.appendVersionToBranch(null, null, null))),
                entry("consumeDownload1", new State(true, s -> s.consumeDownload(null))),
                entry("contentIdFromHash1", new State(false, s -> s.contentIdFromHash(null))),
                entry("countArtifacts0", new State(false, RegistryStorage::countArtifacts)),
                entry("countArtifactVersions2", new State(false, s -> s.countArtifactVersions(null, null))),
                entry("countActiveArtifactVersions2",
                        new State(false, s -> s.countActiveArtifactVersions(null, null))),
                entry("countTotalArtifactVersions0",
                        new State(false, RegistryStorage::countTotalArtifactVersions)),
                entry("createArtifact11",
                        new State(true,
                                s -> s.createArtifact(null, null, null, null, null, null, null, null, false,
                                        false, null))),
                entry("createArtifactRule4",
                        new State(true, s -> s.createArtifactRule(null, null, null, null))),
                entry("createArtifactVersionComment4",
                        new State(true, s -> s.createArtifactVersionComment(null, null, null, null))),
                entry("createArtifactVersion10",
                        new State(true,
                                s -> s.createArtifactVersion(null, null, null, null, null, null, null, false,
                                        false, null))),
                entry("createBranch4", new State(true, s -> s.createBranch(null, null, null, null))),
                entry("createDownload1", new State(true, s -> s.createDownload(null))),
                entry("createGlobalRule2", new State(true, s -> s.createGlobalRule(null, null))),
                entry("createGroup1", new State(true, s -> s.createGroup(null))),
                entry("createRoleMapping3", new State(true, s -> s.createRoleMapping(null, null, null))),
                entry("deleteAllExpiredDownloads0",
                        new State(true, RegistryStorage::deleteAllExpiredDownloads)),
                entry("deleteAllUserData0", new State(true, RegistryStorage::deleteAllUserData)),
                entry("deleteArtifact2", new State(true, s -> s.deleteArtifact(null, null))),
                entry("deleteBranch2", new State(true, s -> s.deleteBranch(null, null))),
                entry("deleteArtifactRule3", new State(true, s -> s.deleteArtifactRule(null, null, null))),
                entry("deleteArtifactRules2", new State(true, s -> s.deleteArtifactRules(null, null))),
                entry("deleteArtifacts1", new State(true, s -> s.deleteArtifacts(null))),
                entry("deleteArtifactVersion3",
                        new State(true, s -> s.deleteArtifactVersion(null, null, null))),
                entry("deleteArtifactVersionComment4",
                        new State(true, s -> s.deleteArtifactVersionComment(null, null, null, null))),
                entry("deleteConfigProperty1", new State(true, s -> s.deleteConfigProperty("test"))),
                entry("deleteGlobalRule1", new State(true, s -> s.deleteGlobalRule(null))),
                entry("deleteGlobalRules0", new State(true, RegistryStorage::deleteGlobalRules)),
                entry("deleteGroup1", new State(true, s -> s.deleteGroup(null))),
                entry("deleteRoleMapping1", new State(true, s -> s.deleteRoleMapping(null))),
                entry("exportData1", new State(false, s -> s.exportData(null))),
                entry("getContentByHash1", new State(false, s -> s.getContentByHash(null))),
                entry("getContentById1", new State(false, s -> s.getContentById(0))),
                entry("getBranchMetaData2", new State(false, s -> s.getBranchMetaData(null, null))),
                entry("getBranches3", new State(false, s -> s.getBranches(null, 0, 0))),
                entry("getBranchVersions4", new State(false, s -> s.getBranchVersions(null, null, 0, 0))),
                entry("getBranchTip3", new State(false, s -> s.getBranchTip(null, null, null))),
                entry("getArtifactIds1", new State(false, s -> s.getArtifactIds(null))),
                entry("getArtifactMetaData2", new State(false, s -> s.getArtifactMetaData(null, null))),
                entry("getArtifactRule3", new State(false, s -> s.getArtifactRule(null, null, null))),
                entry("getArtifactRules2", new State(false, s -> s.getArtifactRules(null, null))),
                entry("getArtifactVersionContent1", new State(false, s -> s.getArtifactVersionContent(0))),
                entry("getArtifactVersionContent3",
                        new State(false, s -> s.getArtifactVersionContent(null, null, null))),
                entry("getArtifactVersionComments3",
                        new State(false, s -> s.getArtifactVersionComments(null, null, null))),
                entry("getArtifactVersionMetaData1",
                        new State(false, s -> s.getArtifactVersionMetaData(null))),
                entry("getArtifactVersionMetaData3",
                        new State(false, s -> s.getArtifactVersionMetaData(null, null, null))),
                entry("getArtifactVersionMetaDataByContent5",
                        new State(false,
                                s -> s.getArtifactVersionMetaDataByContent(null, null, false, null, null))),
                entry("getArtifactVersions2", new State(false, s -> s.getArtifactVersions(null, null))),
                entry("getArtifactVersions3",
                        new State(false,
                                s -> s.getArtifactVersions(null, null,
                                        RegistryStorage.RetrievalBehavior.ALL_STATES))),
                entry("getArtifactVersionState3",
                        new State(false, s -> s.getArtifactVersionState(null, null, null))),
                entry("getEnabledArtifactContentIds2",
                        new State(false, s -> s.getEnabledArtifactContentIds(null, null))),
                entry("getArtifactVersionsByContentId1",
                        new State(false, s -> s.getArtifactVersionsByContentId(0))),
                entry("getConfigProperties0", new State(false, DynamicConfigStorage::getConfigProperties)),
                entry("getConfigProperty1", new State(false, s -> s.getConfigProperty(null))),
                entry("getContentIdsReferencingArtifactVersion3",
                        new State(false, s -> s.getContentIdsReferencingArtifactVersion(null, null, null))),
                entry("getGlobalIdsReferencingArtifactVersion2",
                        new State(false, s -> s.getGlobalIdsReferencingArtifact(null, null))),
                entry("getGlobalIdsReferencingArtifactVersion3",
                        new State(false, s -> s.getGlobalIdsReferencingArtifactVersion(null, null, null))),
                entry("getGlobalRule1", new State(false, s -> s.getGlobalRule(null))),
                entry("getGlobalRules0", new State(false, RegistryStorage::getGlobalRules)),
                entry("getGroupIds1", new State(false, s -> s.getGroupIds(null))),
                entry("getGroupMetaData1", new State(false, s -> s.getGroupMetaData(null))),
                entry("getInboundArtifactReferences3",
                        new State(false, s -> s.getInboundArtifactReferences(null, null, null))),
                entry("getRawConfigProperty1", new State(false, s -> s.getRawConfigProperty(null))),
                entry("getRoleForPrincipal1", new State(false, s -> s.getRoleForPrincipal(null))),
                entry("getRoleMapping1", new State(false, s -> s.getRoleMapping(null))),
                entry("getRoleMappings0", new State(false, RegistryStorage::getRoleMappings)),
                entry("searchRoleMappings2", new State(false, s -> s.searchRoleMappings(0, 20))),
                entry("getStaleConfigProperties1", new State(false, s -> s.getStaleConfigProperties(null))),
                entry("importBranch1", new State(true, s -> s.importBranch(null))),
                entry("importArtifactRule1", new State(true, s -> s.importArtifactRule(null))),
                entry("importArtifact1", new State(true, s -> s.importArtifact(null))),
                entry("importArtifactVersion1", new State(true, s -> s.importArtifactVersion(null))),
                entry("importComment1", new State(true, s -> s.importComment(null))),
                entry("importContent1", new State(true, s -> s.importContent(null))),
                entry("importData3", new State(true, s -> s.importData(null, false, false))),
                entry("importGlobalRule1", new State(true, s -> s.importGlobalRule(null))),
                entry("importGroup1", new State(true, s -> s.importGroup(null))),
                entry("initialize0", new State(false, RegistryStorage::initialize)),
                entry("isAlive0", new State(false, RegistryStorage::isAlive)),
                entry("isEmpty0", new State(false, RegistryStorage::isEmpty)),
                entry("isArtifactExists2", new State(false, s -> s.isArtifactExists(null, null))),
                entry("isArtifactRuleExists3",
                        new State(false, s -> s.isArtifactRuleExists(null, null, null))),
                entry("isArtifactVersionExists3",
                        new State(false, s -> s.isArtifactVersionExists(null, null, null))),
                entry("isContentExists1", new State(false, s -> s.isContentExists(null))),
                entry("isGlobalRuleExists1", new State(false, s -> s.isGlobalRuleExists(null))),
                entry("isGroupExists1", new State(false, s -> s.isGroupExists(null))),
                entry("isReadOnly0", new State(false, RegistryStorage::isReadOnly)),
                entry("isReady0", new State(false, RegistryStorage::isReady)),
                entry("isRoleMappingExists1", new State(false, s -> s.isRoleMappingExists(null))),
                entry("nextCommentId0", new State(true, RegistryStorage::nextCommentId)),
                entry("nextContentId0", new State(true, RegistryStorage::nextContentId)),
                entry("nextGlobalId0", new State(true, RegistryStorage::nextGlobalId)),
                entry("replaceBranchVersions3",
                        new State(true, s -> s.replaceBranchVersions(null, null, null))),
                entry("resetContentId0", new State(true, RegistryStorage::resetContentId)),
                entry("resetCommentId0", new State(true, RegistryStorage::resetCommentId)),
                entry("resetGlobalId0", new State(true, RegistryStorage::resetGlobalId)),
                entry("searchArtifacts5", new State(false, s -> s.searchArtifacts(null, null, null, 0, 0))),
                entry("searchGroups5", new State(false, s -> s.searchGroups(null, null, null, null, null))),
                entry("searchVersions5", new State(false, s -> s.searchVersions(null, null, null, 0, 0))),
                entry("setConfigProperty1", new State(true, s -> {
                    var dto = new DynamicConfigPropertyDto();
                    dto.setName("test");
                    s.setConfigProperty(dto);
                })), entry("storageName0", new State(false, RegistryStorage::storageName)),
                entry("updateArtifactMetaData3",
                        new State(true, s -> s.updateArtifactMetaData(null, null, null))),
                entry("updateArtifactRule4",
                        new State(true, s -> s.updateArtifactRule(null, null, null, null))),
                entry("updateArtifactVersionComment5",
                        new State(true, s -> s.updateArtifactVersionComment(null, null, null, null, null))),
                entry("updateArtifactVersionContent5",
                        new State(true, s -> s.updateArtifactVersionContent(null, null, null, null, null))),
                entry("updateArtifactVersionMetaData4",
                        new State(true, s -> s.updateArtifactVersionMetaData(null, null, null, null))),
                entry("updateBranchMetaData3",
                        new State(true, s -> s.updateBranchMetaData(null, null, null))),
                entry("updateContentCanonicalHash3",
                        new State(true, s -> s.updateContentCanonicalHash(null, 0, null))),
                entry("updateGlobalRule2", new State(true, s -> s.updateGlobalRule(null, null))),
                entry("updateGroupMetaData2", new State(true, s -> s.updateGroupMetaData(null, null))),
                entry("updateRoleMapping2", new State(true, s -> s.updateRoleMapping(null, null))),
                entry("updateArtifactVersionState5",
                        new State(true, s -> s.updateArtifactVersionState(null, null, null, null, false))),

                entry("getGroupRules1", new State(false, s -> s.getGroupRules(null))),
                entry("getGroupRule2", new State(false, s -> s.getGroupRule(null, null))),
                entry("updateGroupRule3", new State(true, s -> s.updateGroupRule(null, null, null))),
                entry("createGroupRule3", new State(true, s -> s.createGroupRule(null, null, null))),
                entry("deleteGroupRule2", new State(true, s -> s.deleteGroupRule(null, null))),
                entry("deleteGroupRules1", new State(true, s -> s.deleteGroupRules(null))),
                entry("importGroupRule1", new State(true, s -> s.importGroupRule(null))),

                entry("triggerSnapshotCreation0", new State(true, RegistryStorage::triggerSnapshotCreation)),
                entry("createSnapshot1", new State(true, s -> s.createSnapshot(null))),
                entry("upgradeData3", new State(true, s -> s.upgradeData(null, false, false))),
                entry("createEvent1", new State(true, s -> s.createEvent(null))),
                entry("supportsDatabaseEvents0", new State(true, s -> s.createEvent(null))),
                entry("getContentByReference1", new State(true, s -> s.getContentByReference(null))));

        CURRENT_METHODS = Arrays.stream(RegistryStorage.class.getMethods())
                .map(m -> m.getName() + m.getParameterCount()).collect(Collectors.toSet());
    }

    @Test
    void readOnlyTest() {
        notEnabled();
        var dto = new DynamicConfigPropertyDto();
        dto.setName("apicurio.storage.read-only.enabled");
        dto.setValue("true");
        storage.setConfigProperty(dto);
        enabled();
        dto.setValue("false");
        storage.setConfigProperty(dto);
        notEnabled();
    }

    private void notEnabled() {
        for (String method : CURRENT_METHODS) {
            State state = EXPECTED_METHODS.get(method);
            assertNotNull(state,
                    "Method " + method + " in RegistryStorage interface is not covered by this test.");
            try {
                state.runnable.run(storage);
            } catch (Exception ex) {
                if (ex instanceof ReadOnlyStorageException) {
                    Assertions.fail("Unexpected ReadOnlyStorageException for method " + method
                            + " (read-only is not enabled).", ex);
                }
            } finally {
                state.executed = true;
            }
        }
        reset();
    }

    private void enabled() {
        for (String method : CURRENT_METHODS) {
            State state = EXPECTED_METHODS.get(method);
            assertNotNull(state,
                    "Method " + method + " in RegistryStorage interface is not covered by this test.");
            try {
                state.runnable.run(storage);
                if (state.writes) {
                    Assertions.fail("Expected ReadOnlyStorageException for method " + method
                            + " (read-only is enabled).");
                }
            } catch (Exception ex) {
                if (ex instanceof ReadOnlyStorageException) {
                    if (!state.writes) {
                        Assertions.fail("Unexpected ReadOnlyStorageException for method " + method
                                + " (read-only is enabled).", ex);
                    }
                } else {
                    if (state.writes) {
                        Assertions.fail("Expected ReadOnlyStorageException for method " + method
                                + " (read-only is enabled).");
                    }
                }
            } finally {
                state.executed = true;
            }
        }
        reset();
    }

    private void reset() {
        var notExecuted = EXPECTED_METHODS.entrySet().stream().filter(e -> !e.getValue().executed)
                .collect(Collectors.toSet());
        Assertions.assertEquals(Set.of(), notExecuted,
                "Some method(s) expected to be in the RegistryStorage interface "
                        + "by this test are missing.");
        EXPECTED_METHODS.forEach((key, value) -> value.executed = false);
    }

    private static class State {

        private boolean writes;
        private Runnable1Ex<RegistryStorage, Exception> runnable;

        private boolean executed;

        public State(boolean writes, Runnable1Ex<RegistryStorage, Exception> runnable) {
            this.writes = writes;
            this.runnable = runnable;
        }
    }
}
