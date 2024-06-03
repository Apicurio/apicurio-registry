package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.Labels;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
public class SearchGroupsTest extends AbstractResourceTestBase {

    @Test
    public void testSearchGroupsByName() throws Exception {
        String groupId = "testSearchGroupsByName";
        // Create 5 groups
        for (int idx = 0; idx < 5; idx++) {
            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId(groupId + idx);
            clientV3.groups().post(createGroup);
        }

        GroupSearchResults results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = groupId + "1";
        });
        Assertions.assertEquals(1, results.getGroups().size());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = "testSearchGroupsByName3";
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByName3", results.getGroups().get(0).getGroupId());
    }

    @Test
    public void testSearchGroupsByDescription() throws Exception {
        String groupId = "testSearchGroupsByDescription";
        // Create 5 groups
        for (int idx = 0; idx < 5; idx++) {
            String description = "Description of group number " + idx;
            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId(groupId + idx);
            createGroup.setDescription(description);
            clientV3.groups().post(createGroup);
        }

        GroupSearchResults results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = groupId + "1";
        });
        Assertions.assertEquals(1, results.getGroups().size());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.description = "Description of group number 3";
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByDescription3", results.getGroups().get(0).getGroupId());
        Assertions.assertEquals("Description of group number 3", results.getGroups().get(0).getDescription());
    }

    @Test
    public void testSearchGroupsByLabels() throws Exception {
        String groupId = "testSearchGroupsByLabels";
        // Create 5 groups
        for (int idx = 0; idx < 5; idx++) {
            Labels labels = new Labels();
            labels.setAdditionalData(Map.of(
                    "byLabels", "byLabels-value-" + idx,
                    "byLabels-" + idx, "byLabels-value-" + idx
            ));

            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId(groupId + idx);
            createGroup.setLabels(labels);
            clientV3.groups().post(createGroup);
        }

        GroupSearchResults results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = groupId + "1";
        });
        Assertions.assertEquals(1, results.getGroups().size());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[]{ "byLabels" };
        });
        Assertions.assertEquals(5, results.getGroups().size());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[]{ "byLabels-3" };
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByLabels3", results.getGroups().get(0).getGroupId());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[]{ "byLabels:byLabels-value-3" };
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByLabels3", results.getGroups().get(0).getGroupId());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[]{ "byLabels-3" };
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByLabels3", results.getGroups().get(0).getGroupId());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[]{ "byLabels-3:byLabels-value-3" };
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByLabels3", results.getGroups().get(0).getGroupId());
    }

}
