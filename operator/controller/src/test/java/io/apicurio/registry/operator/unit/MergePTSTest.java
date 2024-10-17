package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.utils.PodTemplateSpecUtils;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class MergePTSTest {
    private static final Logger log = LoggerFactory.getLogger(MergePTSTest.class);

    private static final String TEST_CONTAINER_NAME = "test";

    @Test
    void shouldReturnAnErrorStatus() {
        var testCases = deserialize("podtemplatespec-test-cases.yaml", TestCases.class);
        for (var testCase : testCases.cases) {
            log.info("Running test case: {}", testCase.id);
            var expectedException = Boolean.TRUE.equals(testCase.expectedException);
            try {
                var expected = PodTemplateSpecUtils.mergePTS(testCase.spec, testCase.original,
                        TEST_CONTAINER_NAME);
                if (!expectedException) {
                    // spotless:off
                    assertThat(expected)
                            .usingRecursiveComparison()
                            .ignoringCollectionOrderInFields("spec.containers", "spec.containers.ports")
                            .isEqualTo(testCase.expected);
                    assertThat(testCase.expected)
                            .usingRecursiveComparison()
                            .ignoringCollectionOrderInFields("spec.containers", "spec.containers.ports")
                            .isEqualTo(expected);
                    // spotless:on
                } else {
                    fail("OperatorException expected");
                }
            } catch (OperatorException ex) {
                if (!expectedException) {
                    throw ex;
                }
            }
        }
    }

    @Getter
    @Setter
    public static class TestCases {
        private List<TestCase> cases;
    }

    @Getter
    @Setter
    public static class TestCase {
        private String id;
        private PodTemplateSpec original;
        private PodTemplateSpec spec;
        private PodTemplateSpec expected;
        private Boolean expectedException;
    }
}
