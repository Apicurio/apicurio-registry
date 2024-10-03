package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.OperatorException;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.restassured.specification.RequestSpecification;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;

import static io.apicurio.registry.operator.it.ITBase.INGRESS_HOST_PROP;
import static io.apicurio.registry.operator.it.ITBase.INGRESS_SKIP_PROP;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public class IngressManager {

    private static final Logger log = LoggerFactory.getLogger(IngressManager.class);

    private final KubernetesClient k8sClient;

    private final String namespace;

    public IngressManager(KubernetesClient k8sClient, String namespace) {
        this.k8sClient = k8sClient;
        this.namespace = namespace;
    }

    public String getIngressHost(String prefix) {
        var rand = UUID.randomUUID().toString().substring(0, 6);
        return prefix + "." + rand + "." + namespace + getBaseIngressHost().map(v -> "." + v).orElse("");
    }

    private static boolean isSkipIngress() {
        return ConfigProvider.getConfig().getOptionalValue(INGRESS_SKIP_PROP, Boolean.class).orElse(false);
    }

    private static Optional<String> getBaseIngressHost() {
        return ConfigProvider.getConfig().getOptionalValue(INGRESS_HOST_PROP, String.class);
    }

    public boolean isIngressSupported() {
        if (isSkipIngress()) {
            log.warn("Ingress testing is skipped. This is not recommended.");
            return false;
        }
        return true;
    }

    public RequestSpecification startHttpRequest(String ingressName) {
        if (!isIngressSupported()) {
            throw new OperatorException("Ingress tests are not supported.");
        }

        var ingress = k8sClient.network().v1().ingresses().inNamespace(namespace).withName(ingressName).get();
        assertThat(ingress).isNotNull();

        String host = null;
        if (ingress.getSpec().getRules().size() == 1) {
            host = ingress.getSpec().getRules().get(0).getHost();
        }

        String loadBalancerIP = null;
        if (!ingress.getStatus().getLoadBalancer().getIngress().isEmpty()) {
            loadBalancerIP = ingress.getStatus().getLoadBalancer().getIngress().get(0).getIp();
        }

        log.debug("Ingress {} host: {}", ingressName, host);
        log.debug("Ingress {} loadBalancerIP: {}", ingressName, loadBalancerIP);

        if (host != null) {
            if (loadBalancerIP != null) {
                // spotless:off
                return given()
                        .baseUri("http://" + loadBalancerIP)
                        .port(80)
                        .header("Host", host);
                // spotless:on
            } else {
                // spotless:off
                return given()
                        .baseUri("http://" + host)
                        .port(80);
                // spotless:on
            }
        } else {
            throw new OperatorException("Ingress " + ingressName + " does not have a single host.");
        }
    }
}
