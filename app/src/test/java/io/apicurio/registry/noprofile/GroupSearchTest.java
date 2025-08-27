package io.apicurio.registry.noprofile;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.models.GroupSearchResults;
import io.apicurio.registry.rest.client.models.Labels;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

@QuarkusTest
public class GroupSearchTest extends AbstractResourceTestBase {

    @Test
    void testFilterByLabels() throws Exception {
        String groupIdPrefix = TestUtils.generateGroupId();
        String commonDescription = "GroupSearchTest description " + groupIdPrefix;

        Labels labelsAlpha = new Labels();
        labelsAlpha.setAdditionalData(Map.of("kind", "alpha"));
        Labels labelsBeta = new Labels();
        labelsBeta.setAdditionalData(Map.of("kind", "beta"));

        createGroup(groupIdPrefix + "-g1", commonDescription, labelsAlpha, null);
        createGroup(groupIdPrefix + "-g2", commonDescription, labelsAlpha, null);
        createGroup(groupIdPrefix + "-g3", commonDescription, labelsBeta, null);

        GroupSearchResults results = clientV3.search().groups().get(config -> {
            config.queryParameters.description = commonDescription;
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getGroups().size());

        results = clientV3.search().groups().get(config -> {
            config.queryParameters.description = commonDescription;
            config.queryParameters.labels = new String[] { "kind:alpha" };
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(2, results.getGroups().size());

        results = clientV3.search().groups().get(config -> {
            config.queryParameters.description = commonDescription;
            config.queryParameters.labels = new String[] { "kind:beta" };
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(1, results.getGroups().size());

        // Label filter with key only (no label value)
        results = clientV3.search().groups().get(config -> {
            config.queryParameters.description = commonDescription;
            config.queryParameters.labels = new String[] { "kind" };
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getGroups().size());

        // Label filter with key only (no label value)
        results = clientV3.search().groups().get(config -> {
            config.queryParameters.description = commonDescription;
            config.queryParameters.labels = new String[] { "kind:" };
        });
        Assertions.assertNotNull(results);
        Assertions.assertEquals(3, results.getGroups().size());

    }
}


