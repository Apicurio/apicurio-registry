package io.apicurio.registry.noprofile.rest.v3;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.Labels;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

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
            labels.setAdditionalData(
                    Map.of("byLabels", "byLabels-value-" + idx, "byLabels-" + idx, "byLabels-value-" + idx));

            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId(groupId + idx);
            createGroup.setLabels(labels);
            clientV3.groups().post(createGroup);
        }

        GroupSearchResults results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = groupId + "1";
        });
        Assertions.assertEquals(1, results.getGroups().size());
        // Note: ensure that labels are returned in the search results
        Assertions.assertNotNull(results.getGroups().get(0).getLabels());
        Assertions.assertEquals(Map.of("byLabels", "byLabels-value-1", "byLabels-1", "byLabels-value-1"),
                results.getGroups().get(0).getLabels().getAdditionalData());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[] { "byLabels" };
        });
        Assertions.assertEquals(5, results.getGroups().size());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[] { "byLabels-3" };
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByLabels3", results.getGroups().get(0).getGroupId());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[] { "byLabels:byLabels-value-3" };
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByLabels3", results.getGroups().get(0).getGroupId());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[] { "byLabels-3" };
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByLabels3", results.getGroups().get(0).getGroupId());

        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[] { "byLabels-3:byLabels-value-3" };
        });
        Assertions.assertEquals(1, results.getGroups().size());
        Assertions.assertEquals("testSearchGroupsByLabels3", results.getGroups().get(0).getGroupId());
    }

    @Test
    public void testSearchGroupsByGroupIdWildcard() throws Exception {
        String prefix = "WildcardGroupSearch_" + UUID.randomUUID().toString().substring(0, 8);

        for (int idx = 0; idx < 3; idx++) {
            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId(prefix + "_group_" + idx);
            clientV3.groups().post(createGroup);
        }

        // Prefix wildcard
        GroupSearchResults results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = prefix + "*";
        });
        Assertions.assertEquals(3, results.getGroups().size(),
                "Wildcard groupId prefix should return all 3 groups");

        // Substring wildcard
        results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = "*" + prefix + "*";
        });
        Assertions.assertEquals(3, results.getGroups().size(),
                "Wildcard substring should return all 3 groups");

        // Exact match
        results = clientV3.search().groups().get(request -> {
            request.queryParameters.groupId = prefix + "_group_1";
        });
        Assertions.assertEquals(1, results.getGroups().size(),
                "Exact match should return 1 group");
    }

    @Test
    public void testSearchGroupsByLabelWildcard() throws Exception {
        String prefix = "WildcardLabelGroup_" + UUID.randomUUID().toString().substring(0, 8);

        for (int idx = 0; idx < 3; idx++) {
            Labels labels = new Labels();
            labels.setAdditionalData(
                    Map.of("env.tier-" + idx, "value-" + idx, "common", "shared"));
            CreateGroup createGroup = new CreateGroup();
            createGroup.setGroupId(prefix + "_" + idx);
            createGroup.setLabels(labels);
            clientV3.groups().post(createGroup);
        }

        // Wildcard on label key
        GroupSearchResults results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[] { "env.*" };
        });
        Assertions.assertEquals(3, results.getGroups().size(),
                "Wildcard label key 'env.*' should match all 3 groups");

        // Wildcard on label value
        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[] { "common:sha*" };
        });
        Assertions.assertEquals(3, results.getGroups().size(),
                "Wildcard label value 'common:sha*' should match all 3 groups");

        // Exact label key still works
        results = clientV3.search().groups().get(request -> {
            request.queryParameters.labels = new String[] { "env.tier-1" };
        });
        Assertions.assertEquals(1, results.getGroups().size(),
                "Exact label key should return 1 group");
    }
}
