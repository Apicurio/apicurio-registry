package io.apicurio.registry.noprofile.rest.v3;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroupMetaData;
import io.apicurio.registry.rest.client.models.EditableGroupMetaData;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.Labels;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class GroupMetaDataTest extends AbstractResourceTestBase {
    
    @Test
    public void createGroupWithMetadata() throws Exception {
        String groupId = UUID.randomUUID().toString();
        Map<String, Object> labels = Map.of("label-1", "value-1", "label-2", "value-2");
        
        Labels l = new Labels();
        l.setAdditionalData(labels);

        CreateGroupMetaData body = new CreateGroupMetaData();
        body.setId(groupId);
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

        CreateGroupMetaData body = new CreateGroupMetaData();
        body.setId(groupId);
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
        Map<String, Object> labels2 = Map.of("label-5", "value-5", "label-6", "value-6", "label-7", "value-7");
        
        Labels l = new Labels();
        l.setAdditionalData(labels1);

        CreateGroupMetaData body = new CreateGroupMetaData();
        body.setId(groupId);
        body.setDescription("My favorite test group.");
        body.setLabels(l);
        clientV3.groups().post(body);

        EditableGroupMetaData egmd = new EditableGroupMetaData();
        egmd.setDescription("UPDATED DESCRIPTION");
        l.setAdditionalData(labels2);
        egmd.setLabels(l);
        // Update the metadata
        clientV3.groups().byGroupId(groupId).put(egmd);

        // Now fetch the metadata
        GroupMetaData gmd = clientV3.groups().byGroupId(groupId).get();

        Assertions.assertEquals(groupId, gmd.getGroupId());
        Assertions.assertEquals("UPDATED DESCRIPTION", gmd.getDescription());
        Assertions.assertEquals(labels2, gmd.getLabels().getAdditionalData());
    }

}
