package io.apicurio.registry.operator.unit;

import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import java.util.List;
import java.util.stream.Stream;

import static io.apicurio.registry.operator.resource.ResourceFactory.deserialize;

public class PodTemplateSpecArgumentProviders {

    private PodTemplateSpecArgumentProviders() {
    }

    public static class AppPositiveTestCases implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return deserialize("podtemplatespec-test-cases-app-positive.yaml",
                    PodTemplateSpecArgumentProviders.TestCases.class).getCases().stream().map(Arguments::of);
        }
    }

    public static class AppNegativeTestCases implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return deserialize("podtemplatespec-test-cases-app-negative.yaml",
                    PodTemplateSpecArgumentProviders.TestCases.class).getCases().stream().map(Arguments::of);
        }
    }

    public static class UIPositiveTestCases implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return deserialize("podtemplatespec-test-cases-ui-positive.yaml",
                    PodTemplateSpecArgumentProviders.TestCases.class).getCases().stream().map(Arguments::of);
        }
    }

    public static class UINegativeTestCases implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
            return deserialize("podtemplatespec-test-cases-ui-negative.yaml",
                    PodTemplateSpecArgumentProviders.TestCases.class).getCases().stream().map(Arguments::of);
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
        private PodTemplateSpec spec;
        private PodTemplateSpec expected;
    }
}
