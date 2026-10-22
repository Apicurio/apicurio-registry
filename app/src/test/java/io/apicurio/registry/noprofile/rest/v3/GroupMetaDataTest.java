package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.EditableGroupMetaData;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

@QuarkusTest
public class GroupMetaDataTest extends AbstractResourceTestBase {

    @Test
    public void createGroupWithMetadata() throws Exception {
        String groupId = UUID.randomUUID().toString();
        Map<String, Object> labels = Map.of("label-1", "value-1", "label-2", "value-2");

        Labels l = new Labels();
        l.setAdditionalData(labels);

        CreateGroup body = new CreateGroup();
        body.setGroupId(groupId);
        body.setDescription("My favorite test group.");
        body.setLabels(l);
        GroupMetaData gmd = clientV3.groups().post(body);

        Assertions.assertEquals(groupId, gmd.getGroupId());
        Assertions.assertEquals("My favorite test group.", gmd.getDescription());
        Assertions.assertEquals(labels, gmd.getLabels().getAdditionalData());
    }

    @Test
    public void getGroupMetadata() throws Exception {
        String groupId = UUID.randomUUID().toString();
        Map<String, Object> labels = Map.of("label-1", "value-1", "label-2", "value-2");

        Labels l = new Labels();
        l.setAdditionalData(labels);

        CreateGroup body = new CreateGroup();
        body.setGroupId(groupId);
        body.setDescription("My favorite test group.");
        body.setLabels(l);
        clientV3.groups().post(body);

        // Now fetch the metadata
        GroupMetaData gmd = clientV3.groups().byGroupId(groupId).get();

        Assertions.assertEquals(groupId, gmd.getGroupId());
        Assertions.assertEquals("My favorite test group.", gmd.getDescription());
        Assertions.assertEquals(labels, gmd.getLabels().getAdditionalData());
    }

    @Test
    public void updateGroupMetadata() throws Exception {
        String groupId = UUID.randomUUID().toString();
        Map<String, Object> labels1 = Map.of("label-1", "value-1", "label-2", "value-2");
        Map<String, Object> labels2 = Map.of("label-5", "value-5", "label-6", "value-6", "label-7",
                "value-7");

        Labels l = new Labels();
        l.setAdditionalData(labels1);

        CreateGroup body = new CreateGroup();
        body.setGroupId(groupId);
        body.setDescription("My favorite test group.");
        body.setLabels(l);
        clientV3.groups().post(body);

        EditableGroupMetaData egmd = new EditableGroupMetaData();
        egmd.setDescription("UPDATED DESCRIPTION");
        egmd.setOwner("owner");
        l.setAdditionalData(labels2);
        egmd.setLabels(l);
        // Update the metadata
        clientV3.groups().byGroupId(groupId).put(egmd);

        // Now fetch the metadata
        GroupMetaData gmd = clientV3.groups().byGroupId(groupId).get();

        Assertions.assertEquals(groupId, gmd.getGroupId());
        Assertions.assertEquals("UPDATED DESCRIPTION", gmd.getDescription());
        Assertions.assertEquals("owner", gmd.getOwner());
        Assertions.assertEquals(labels2, gmd.getLabels().getAdditionalData());
    }

    @Test
    public void updateOnlyDescriptionInGroupMetadata() throws Exception {
        final String groupId = UUID.randomUUID().toString();

        final Map<String, Object> labels = Map.of(
                "label-1", "value-1",
                "label-2", "value-2"
        );

        final Labels l = new Labels();
        l.setAdditionalData(labels);

        // Create group
        final CreateGroup body = new CreateGroup();
        body.setGroupId(groupId);
        body.setDescription("initial-description");
        body.setLabels(l);
        clientV3.groups().post(body);

        // Set owner
        final EditableGroupMetaData setOwnerEgmd = new EditableGroupMetaData();
        setOwnerEgmd.setOwner("owner");
        clientV3.groups().byGroupId(groupId).put(setOwnerEgmd);

        // Update only description
        final EditableGroupMetaData updateDescriptionEgmd = new EditableGroupMetaData();
        updateDescriptionEgmd.setDescription("updated-description");
        clientV3.groups().byGroupId(groupId).put(updateDescriptionEgmd);

        // Fetch and verify
        final GroupMetaData gmd = clientV3.groups().byGroupId(groupId).get();

        Assertions.assertEquals(groupId, gmd.getGroupId());
        Assertions.assertEquals("updated-description", gmd.getDescription());
        Assertions.assertEquals("owner", gmd.getOwner());
        Assertions.assertEquals(labels, gmd.getLabels().getAdditionalData());
    }

    @Test
    public void updateOnlyLabelsInGroupMetadata() throws Exception {
        final String groupId = UUID.randomUUID().toString();

        final Map<String, Object> labels1 = Map.of(
                "label-1", "value-1",
                "label-2", "value-2"
        );
        final Map<String, Object> labels2 = Map.of(
                "label-3", "value-3",
                "label-4", "value-4"
        );

        final Labels initialLabels = new Labels();
        initialLabels.setAdditionalData(labels1);

        // Create group
        final CreateGroup body = new CreateGroup();
        body.setGroupId(groupId);
        body.setDescription("initial-description");
        body.setLabels(initialLabels);
        clientV3.groups().post(body);

        // Set owner
        final EditableGroupMetaData setOwnerEgmd = new EditableGroupMetaData();
        setOwnerEgmd.setOwner("owner");
        clientV3.groups().byGroupId(groupId).put(setOwnerEgmd);

        // Update only labels
        final Labels updatedLabels = new Labels();
        updatedLabels.setAdditionalData(labels2);

        final EditableGroupMetaData updateLabelsEgmd = new EditableGroupMetaData();
        updateLabelsEgmd.setLabels(updatedLabels);
        clientV3.groups().byGroupId(groupId).put(updateLabelsEgmd);

        // Fetch and verify
        final GroupMetaData gmd = clientV3.groups().byGroupId(groupId).get();

        Assertions.assertEquals(groupId, gmd.getGroupId());
        Assertions.assertEquals("initial-description", gmd.getDescription());
        Assertions.assertEquals("owner", gmd.getOwner());
        Assertions.assertEquals(labels2, gmd.getLabels().getAdditionalData());
    }

    @Test
    public void updateOnlyOwnerInGroupMetadata() throws Exception {
        final String groupId = UUID.randomUUID().toString();

        final Map<String, Object> labels = Map.of(
                "label-1", "value-1",
                "label-2", "value-2"
        );

        final Labels l = new Labels();
        l.setAdditionalData(labels);

        // Create group
        final CreateGroup body = new CreateGroup();
        body.setGroupId(groupId);
        body.setDescription("description");
        body.setLabels(l);
        clientV3.groups().post(body);

        // Set owner
        final EditableGroupMetaData setOwnerEgmd = new EditableGroupMetaData();
        setOwnerEgmd.setOwner("initial-owner");
        clientV3.groups().byGroupId(groupId).put(setOwnerEgmd);

        // Fetch and verify owner
        final GroupMetaData gmd = clientV3.groups().byGroupId(groupId).get();
        Assertions.assertEquals("initial-owner", gmd.getOwner());

        // Update owner
        final EditableGroupMetaData updateOwnerEgmd = new EditableGroupMetaData();
        updateOwnerEgmd.setOwner("updated-owner");
        clientV3.groups().byGroupId(groupId).put(updateOwnerEgmd);

        // Fetch and verify
        final GroupMetaData updatedGmd = clientV3.groups().byGroupId(groupId).get();

        Assertions.assertEquals(groupId, updatedGmd.getGroupId());
        Assertions.assertEquals("updated-owner", updatedGmd.getOwner());
        Assertions.assertEquals("description", updatedGmd.getDescription());
        Assertions.assertEquals(labels, updatedGmd.getLabels().getAdditionalData());
    }
}
