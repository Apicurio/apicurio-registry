package io.apicurio.registry.operator.it;

import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;

/**
 * Reads File-Based Catalog (FBC) content from a catalogd-served {@code ClusterCatalog} (OLM v1).
 * <p>
 * catalogd only exposes its HTTP API in-cluster (no route/ingress, self-signed TLS), so there is no
 * direct network path from the test JVM. This spins up a throwaway pod that curls the catalogd service
 * and reads the response back from the pod's logs. The base URL is read from the ClusterCatalog's
 * {@code status.urls.base}, since the catalogd service namespace varies across OCP versions
 * ({@code olmv1-system} on older, {@code openshift-catalogd} on 4.22+).
 */
public final class CatalogdClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogdClient.class);

    private CatalogdClient() {
    }

    /**
     * Fetches the full FBC content of {@code catalogName} by running a curl pod in {@code namespace}.
     */
    @SuppressWarnings("unchecked")
    public static String readCatalogContent(KubernetesClient client, String namespace, String catalogName)
            throws Exception {
        var cc = client.genericKubernetesResources("olm.operatorframework.io/v1", "ClusterCatalog")
                .withName(catalogName)
                .get();
        if (cc == null) {
            throw new IllegalStateException("ClusterCatalog '" + catalogName + "' not found");
        }

        var urls = (Map<String, Object>) cc.get("status", "urls");
        String baseUrl;
        if (urls != null && urls.get("base") != null) {
            baseUrl = (String) urls.get("base");
            log.info("Discovered catalogd base URL from ClusterCatalog status: {}", baseUrl);
        } else {
            // Fallback for older OLM v1 versions that may not populate status.urls
            baseUrl = "https://catalogd-service.openshift-catalogd.svc/catalogs/" + catalogName;
            log.warn("ClusterCatalog status.urls.base not available, using fallback: {}", baseUrl);
        }

        var podName = "catalog-query-" + namespace.substring(namespace.length() - 7);

        try {
            client.pods().inNamespace(namespace).withName(podName).delete();
            Thread.sleep(2000);
        } catch (Exception e) {
            // ignore: pod may not exist yet
        }

        // Try the /api/v1/all endpoint first, then /all for older catalogd versions
        var curlCmd = "curl -sk '" + baseUrl + "/api/v1/all' 2>/dev/null || "
                + "curl -sk '" + baseUrl + "/all' 2>/dev/null";
        log.info("Querying catalogd API via curl pod: {}", curlCmd);

        var pod = new PodBuilder()
                .withNewMetadata().withName(podName).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName("curl")
                .withImage("registry.access.redhat.com/ubi9/ubi-minimal:latest")
                .withCommand("sh", "-c", curlCmd)
                .endContainer()
                .endSpec()
                .build();
        client.pods().inNamespace(namespace).resource(pod).create();

        await().atMost(Duration.ofMinutes(2)).ignoreExceptions().until(() -> {
            var p = client.pods().inNamespace(namespace).withName(podName).get();
            return p != null && ("Succeeded".equals(p.getStatus().getPhase())
                    || "Failed".equals(p.getStatus().getPhase()));
        });

        var content = client.pods().inNamespace(namespace).withName(podName).getLog();
        client.pods().inNamespace(namespace).withName(podName).delete();

        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Empty catalog content from catalogd API at " + baseUrl);
        }
        log.info("Received {} bytes from catalogd API", content.length());
        return content;
    }
}
