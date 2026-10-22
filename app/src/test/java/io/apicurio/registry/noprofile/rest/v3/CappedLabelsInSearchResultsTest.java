package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

@QuarkusTest
public class CappedLabelsInSearchResultsTest extends AbstractResourceTestBase {

    @Test
    public void testCappedLabelsInGroupSearch() throws Exception {
        String groupId = TestUtils.generateGroupId();

        // Lots of labels! Too many labels.
        Labels labels = new Labels();
        labels.setAdditionalData(new HashMap<>());
        for (int idx = 1000; idx < 1500; idx++) {
            labels.getAdditionalData().put("test-key-" + idx, "test-value-" + idx);
        }

        // Create a group with all these labels.
        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        createGroup.setLabels(labels);
        clientV3.groups().post(createGroup);

        // Get the group meta data
        GroupMetaData gmd = clientV3.groups().byGroupId(groupId).get();
        Assertions.assertNotNull(gmd);
        Assertions.assertEquals(groupId, gmd.getGroupId());
        Assertions.assertNotNull(gmd.getLabels());
        Assertions.assertEquals(500, gmd.getLabels().getAdditionalData().size());

        // Search for the group.
        GroupSearchResults results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = groupId;
        });
        Assertions.assertEquals(1, results.getGroups().size());

        Assertions.assertNotNull(results.getGroups().get(0).getLabels());
        // Only 19 labels are returned due to the cap/limit enforced on returning labels in searches
        Assertions.assertEquals(19, results.getGroups().get(0).getLabels().getAdditionalData().size());
    }
}
