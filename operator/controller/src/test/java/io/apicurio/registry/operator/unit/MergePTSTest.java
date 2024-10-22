package io.apicurio.registry.operator.unit;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.unit.MergePTSArgumentProviders.MergePTSNegativeTestCases;
import io.apicurio.registry.operator.unit.MergePTSArgumentProviders.MergePTSPositiveTestCases;
import io.apicurio.registry.operator.unit.MergePTSArgumentProviders.TestCase;
import io.apicurio.registry.operator.utils.PodTemplateSpecFeature;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MergePTSTest {
    private static final Logger log = LoggerFactory.getLogger(MergePTSTest.class);

    private static final String TEST_CONTAINER_NAME = "test";

    @ParameterizedTest
    @ArgumentsSource(MergePTSPositiveTestCases.class)
    void testPTSMergingPositive(TestCase testCase) {
        log.info("Running test case: {}", testCase.getId());
        var expected = PodTemplateSpecFeature.merge(testCase.getSpec(), testCase.getOriginal(),
                TEST_CONTAINER_NAME);
        // spotless:off
        assertThat(expected)
                .usingRecursiveComparison()
                .ignoringCollectionOrderInFields("spec.containers", "spec.containers.ports")
                .isEqualTo(testCase.getExpected());
        assertThat(testCase.getExpected())
                .usingRecursiveComparison()
                .ignoringCollectionOrderInFields("spec.containers", "spec.containers.ports")
                .isEqualTo(expected);
        // spotless:on
    }

    @ParameterizedTest
    @ArgumentsSource(MergePTSNegativeTestCases.class)
    void testPTSMergingNegative(TestCase testCase) {
        log.info("Running test case: {}", testCase.getId());
        assertThatThrownBy(() -> {
            PodTemplateSpecFeature.merge(testCase.getSpec(), testCase.getOriginal(), TEST_CONTAINER_NAME);
        }).isInstanceOf(OperatorException.class);
    }
}
