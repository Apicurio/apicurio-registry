package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.Tags;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ParameterContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag(Tags.FEATURE)
class OperatorInfraExtensionTest {

    private final OperatorInfraExtension extension = new OperatorInfraExtension();

    @SuppressWarnings("unused")
    private static void injectable(KubernetesClient client, RegistryAssertions assertions,
            IngressManager ingressManager, PortForwardManager portForwardManager,
            PodLogManager podLogManager, JobManager jobManager, HostAliasManager hostAliasManager,
            @TestNamespace String namespace, String unqualified, Integer unsupported) {
    }

    private static ParameterContext contextFor(int index) throws Exception {
        Method method = OperatorInfraExtensionTest.class.getDeclaredMethod("injectable",
                KubernetesClient.class, RegistryAssertions.class, IngressManager.class,
                PortForwardManager.class, PodLogManager.class, JobManager.class, HostAliasManager.class,
                String.class, String.class, Integer.class);
        Parameter parameter = method.getParameters()[index];
        return new ParameterContext() {
            @Override
            public Parameter getParameter() {
                return parameter;
            }

            @Override
            public int getIndex() {
                return index;
            }

            @Override
            public Optional<Object> getTarget() {
                return Optional.empty();
            }
        };
    }

    private boolean supports(int index) throws Exception {
        return extension.supportsParameter(contextFor(index), null);
    }

    @Test
    void supportsEveryInfrastructureType() throws Exception {
        for (int i = 0; i <= 6; i++) {
            assertThat(supports(i)).as("parameter %d", i).isTrue();
        }
    }

    @Test
    void supportsNamespaceOnlyWhenQualified() throws Exception {
        assertThat(supports(7)).as("@TestNamespace String").isTrue();
        assertThat(supports(8)).as("unqualified String").isFalse();
    }

    @Test
    void rejectsUnknownTypes() throws Exception {
        assertThat(supports(9)).as("Integer").isFalse();
    }

    @Test
    void needsStrimziIsInheritedAndDetectableOnTestClasses() {
        assertThat(KafkaSqlITTest.class.isAnnotationPresent(NeedsStrimzi.class)).isTrue();
        assertThat(KafkaSqlTLSITTest.class.isAnnotationPresent(NeedsStrimzi.class)).isTrue();
        assertThat(KafkaSqlOAuthITTest.class.isAnnotationPresent(NeedsStrimzi.class)).isTrue();
        assertThat(KafkaSqlAccessITTest.class.isAnnotationPresent(NeedsStrimzi.class)).isTrue();
        assertThat(SmokeITTest.class.isAnnotationPresent(NeedsStrimzi.class)).isFalse();
    }

    @Test
    void operatorMutatingTestsAreMarkedDedicated() {
        assertThat(RestrictedNamespaceITTest.class.isAnnotationPresent(DedicatedOperator.class)).isTrue();
        assertThat(LeaderElectionITTest.class.isAnnotationPresent(DedicatedOperator.class)).isTrue();
        assertThat(OperatorConfigITTest.class.isAnnotationPresent(DedicatedOperator.class)).isTrue();
        assertThat(SmokeITTest.class.isAnnotationPresent(DedicatedOperator.class)).isFalse();
    }
}
