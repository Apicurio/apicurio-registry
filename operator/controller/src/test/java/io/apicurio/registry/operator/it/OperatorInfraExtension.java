package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.OperatorTestContext;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.apicurio.registry.operator.it.ITBase.CLEANUP;
import static io.apicurio.registry.operator.it.ITBase.DEPLOYMENT_TARGET;
import static io.apicurio.registry.operator.it.ITBase.OPERATOR_DEPLOYMENT_PROP;
import static io.apicurio.registry.operator.it.ITBase.calculateNamespace;
import static io.apicurio.registry.operator.it.ITBase.configureRestAssured;
import static io.apicurio.registry.operator.it.ITBase.createK8sClient;
import static io.apicurio.registry.operator.it.ITBase.createNamespace;
import static io.apicurio.registry.operator.it.ITBase.setDefaultAwaitilityTimings;
import static org.eclipse.microprofile.config.ConfigProvider.getConfig;

public class OperatorInfraExtension
        implements BeforeAllCallback, AfterEachCallback, AfterAllCallback, ParameterResolver {

    private static final Logger log = LoggerFactory.getLogger(OperatorInfraExtension.class);

    private static final Namespace STORE = Namespace.create(OperatorInfraExtension.class);

    private static final String SHARED_STRIMZI = "shared-strimzi";

    private static final Map<Class<?>, Function<ClassInfra, Object>> INJECTABLE = Map.of(
            KubernetesClient.class, infra -> infra.client,
            RegistryAssertions.class, infra -> infra.assertions,
            IngressManager.class, infra -> infra.ingressManager,
            PortForwardManager.class, infra -> infra.portForwardManager,
            PodLogManager.class, infra -> infra.podLogManager,
            JobManager.class, infra -> infra.jobManager,
            HostAliasManager.class, infra -> infra.hostAliasManager);

    @Override
    public void beforeAll(ExtensionContext context) {
        setDefaultAwaitilityTimings();
        configureRestAssured();
        var infra = new ClassInfra(context.getRequiredTestClass().getSimpleName());
        context.getStore(STORE).put(ClassInfra.class, infra);
        context.getStore(OperatorTestContext.STORE_NAMESPACE).put(OperatorTestContext.class, infra);
        sharedOperator(context, infra);
        if (context.getRequiredTestClass().isAnnotationPresent(NeedsStrimzi.class)) {
            sharedStrimzi(context, infra);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        var infra = classInfra(context);
        if (infra.cleanup) {
            ITBase.deleteRegistryCRs(infra.client, infra.namespace);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        classInfra(context).tearDown();
    }

    private static ClassInfra classInfra(ExtensionContext context) {
        var infra = context.getStore(STORE).get(ClassInfra.class, ClassInfra.class);
        if (infra == null) {
            throw new IllegalStateException("No infrastructure for "
                    + context.getRequiredTestClass().getName()
                    + ". Is @ExtendWith(OperatorInfraExtension.class) present on the test class?");
        }
        return infra;
    }

    private static void sharedOperator(ExtensionContext context, ClassInfra infra) {
        context.getRoot().getStore(STORE).getOrComputeIfAbsent(SharedOperator.class,
                key -> new SharedOperator(infra), SharedOperator.class);
    }

    private static void sharedStrimzi(ExtensionContext context, ClassInfra infra) {
        context.getRoot().getStore(STORE).getOrComputeIfAbsent(SHARED_STRIMZI, key -> {
            try {
                StrimziClusterWideInstaller.ensureInstalled(infra.client);
            } catch (IOException e) {
                throw new IllegalStateException("Could not install Strimzi", e);
            }
            return Boolean.TRUE;
        });
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        var type = parameterContext.getParameter().getType();
        return type == String.class ? parameterContext.isAnnotated(TestNamespace.class)
                : INJECTABLE.containsKey(type);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        var infra = classInfra(extensionContext);
        var type = parameterContext.getParameter().getType();
        return type == String.class ? infra.namespace : INJECTABLE.get(type).apply(infra);
    }

    static final class ClassInfra implements OperatorTestContext {

        final ITBase.OperatorDeployment deploymentType;
        final String deploymentTarget;
        final boolean cleanup;
        final String namespace;
        final KubernetesClient client;
        final RegistryAssertions assertions;
        final PortForwardManager portForwardManager;
        final IngressManager ingressManager;
        final PodLogManager podLogManager;
        final HostAliasManager hostAliasManager;
        final JobManager jobManager;

        ClassInfra(String testClassName) {
            deploymentType = getConfig().getValue(OPERATOR_DEPLOYMENT_PROP, ITBase.OperatorDeployment.class);
            deploymentTarget = getConfig().getValue(DEPLOYMENT_TARGET, String.class);
            cleanup = getConfig().getValue(CLEANUP, Boolean.class);
            namespace = calculateNamespace();
            client = createK8sClient(namespace);
            createNamespace(client, namespace);
            log.info("Namespace {} created for {}", namespace, testClassName);
            assertions = new RegistryAssertions(client, namespace);
            portForwardManager = new PortForwardManager(namespace);
            ingressManager = new IngressManager(client, namespace);
            podLogManager = new PodLogManager(client);
            hostAliasManager = new HostAliasManager(client);
            jobManager = new JobManager(client, hostAliasManager);
        }

        @Override
        public KubernetesClient getClient() {
            return client;
        }

        @Override
        public String getNamespace() {
            return namespace;
        }

        @Override
        public List<String> extraDiagnosticNamespaces() {
            return List.of(StrimziClusterWideInstaller.strimziNamespace(),
                    OperatorClusterWideInstaller.operatorNamespace());
        }

        void tearDown() throws Exception {
            portForwardManager.close();
            podLogManager.stopAndWait();
            if (cleanup) {
                log.info("Deleting namespace {}", namespace);
                client.namespaces().withName(namespace).delete();
            }
            client.close();
        }
    }

    static final class SharedOperator implements AutoCloseable {

        SharedOperator(ClassInfra infra) {
            try {
                SharedOperatorLifecycle.ensureStarted(infra.client, infra.deploymentType,
                        infra.deploymentTarget);
            } catch (Exception e) {
                throw new IllegalStateException("Could not start the shared operator", e);
            }
        }

        @Override
        public void close() {
            SharedOperatorLifecycle.stopLocalOperator();
        }
    }
}
