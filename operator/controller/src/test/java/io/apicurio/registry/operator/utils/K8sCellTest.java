package io.apicurio.registry.operator.utils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.StatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static io.apicurio.registry.operator.utils.K8sCell.k8sCell;
import static io.apicurio.registry.operator.utils.K8sCell.k8sCellCreate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * There is no Fabric8 mock-server or Mockito usage in the operator test tree.
 * These tests stub {@link KubernetesClient#resource(HasMetadata)} with a JDK proxy.
 */
class K8sCellTest {

    @Test
    void createFailurePropagatesOriginalException() {
        var item = configMap("cm");
        var original = new KubernetesClientException("already exists", 409,
                new StatusBuilder().withReason("AlreadyExists").withCode(409).build());
        KubernetesClient client = stubClient(new StubBehavior() {
            @Override
            public HasMetadata create(HasMetadata resource) {
                throw original;
            }
        });

        var cell = k8sCellCreate(client, () -> item);

        assertThatThrownBy(cell::get)
                .isSameAs(original)
                .satisfies(ex -> assertThat(((KubernetesClientException) ex).getCode()).isEqualTo(409));
    }

    @Test
    void updateRetriesOnHttp409RegardlessOfMessage() {
        var item = configMap("cm");
        var conflict = new KubernetesClientException("Operation cannot be fulfilled on configmaps \"cm\"",
                409, new StatusBuilder().withCode(409).build());
        assertThat(conflict.getMessage()).doesNotContain("the object has been modified");

        AtomicInteger updates = new AtomicInteger();
        KubernetesClient client = stubClient(new StubBehavior() {
            @Override
            public HasMetadata update(HasMetadata resource) {
                if (updates.getAndIncrement() == 0) {
                    throw conflict;
                }
                return resource;
            }
        });

        k8sCell(client, () -> item).update(cm -> cm.getMetadata().getLabels().put("k", "v"));

        assertThat(updates.get()).isEqualTo(2);
        assertThat(item.getMetadata().getLabels()).containsEntry("k", "v");
    }

    @Test
    void updateDoesNotRetryOnLegacyConflictMessageWithoutHttp409() {
        var item = configMap("cm");
        var nonConflict = new KubernetesClientException(
                "the object has been modified; please apply your changes to the latest version and try again",
                500, new StatusBuilder().withCode(500).build());

        AtomicInteger updates = new AtomicInteger();
        KubernetesClient client = stubClient(new StubBehavior() {
            @Override
            public HasMetadata update(HasMetadata resource) {
                updates.incrementAndGet();
                throw nonConflict;
            }
        });

        var cell = k8sCell(client, () -> item);

        assertThatThrownBy(() -> cell.update(cm -> cm.getMetadata().getLabels().put("k", "v")))
                .isInstanceOf(KubernetesClientException.class)
                .hasMessageContaining("the object has been modified");

        assertThat(updates.get()).isEqualTo(1);
    }

    @Test
    void isConflictMatchesCodeOrReason() {
        var byCode = new KubernetesClientException("no useful message", 409, null);
        var byReason = new KubernetesClientException("also no 409 in the text", 500,
                new StatusBuilder().withReason("Conflict").build());
        var other = new KubernetesClientException("forbidden", 403,
                new StatusBuilder().withReason("Forbidden").withCode(403).build());

        assertThat(K8sCell.isConflict(byCode)).isTrue();
        assertThat(K8sCell.isConflict(byReason)).isTrue();
        assertThat(K8sCell.isConflict(other)).isFalse();
    }

    @Test
    void isRetryableTimeoutMatchesOnlyTimeoutMessage() {
        var timeout = new KubernetesClientException("Read timeout", -1, null);
        var other = new KubernetesClientException("forbidden", 403,
                new StatusBuilder().withReason("Forbidden").withCode(403).build());

        assertThat(K8sCell.isRetryableTimeout(timeout)).isTrue();
        assertThat(K8sCell.isRetryableTimeout(other)).isFalse();
    }

    private static ConfigMap configMap(String name) {
        return new ConfigMapBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("ns")
                .withResourceVersion("1")
                .addToLabels("k", "old")
                .endMetadata()
                .build();
    }

    private interface StubBehavior {
        default HasMetadata create(HasMetadata resource) {
            return resource;
        }

        default HasMetadata get(HasMetadata resource) {
            return resource;
        }

        default HasMetadata update(HasMetadata resource) {
            return resource;
        }
    }

    @SuppressWarnings("unchecked")
    private static KubernetesClient stubClient(StubBehavior behavior) {
        return (KubernetesClient) Proxy.newProxyInstance(
                KubernetesClient.class.getClassLoader(),
                new Class<?>[]{KubernetesClient.class},
                (proxy, method, args) -> {
                    if ("resource".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof HasMetadata item) {
                        return resourceStub(item, behavior);
                    }
                    if ("toString".equals(method.getName())) {
                        return "stub-kubernetes-client";
                    }
                    if ("hashCode".equals(method.getName())) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(method.getName())) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException(method.toString());
                });
    }

    @SuppressWarnings("unchecked")
    private static NamespaceableResource<HasMetadata> resourceStub(HasMetadata item, StubBehavior behavior) {
        return (NamespaceableResource<HasMetadata>) Proxy.newProxyInstance(
                NamespaceableResource.class.getClassLoader(),
                new Class<?>[]{NamespaceableResource.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "create" -> behavior.create(item);
                    case "get" -> behavior.get(item);
                    case "update" -> behavior.update(item);
                    case "toString" -> "stub-resource";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }
}
