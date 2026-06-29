package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.RetryTest;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.Tags.OLM;
import static io.apicurio.registry.operator.it.OLMTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Validates OLM catalog channel configuration.
 *
 * Under OLM v0, uses the PackageManifest API. Under OLM v1, reads the catalog content directly from the
 * catalog pod since PackageManifest may return data from other catalog sources.
 */
@QuarkusTest
@Tag(OLM)
public class ChannelValidationOLMITTest extends OLMITBase {

    private static final Logger log = LoggerFactory.getLogger(ChannelValidationOLMITTest.class);

    @Override
    @AfterEach
    public void afterEach() {
    }

    private int getOlmVersion() {
        return ConfigProvider.getConfig().getOptionalValue(OLM_VERSION_PROP, Integer.class)
                .orElse(0);
    }

    @RetryTest
    void testExpectedChannelsExist() {
        var projectVersion = getProjectVersion();
        var minorChannel = deriveMinorChannel(projectVersion);
        var rollingChannel = deriveRollingChannel(projectVersion);

        log.info("Validating channels for version {}. Expected rolling={}, minor={}",
                projectVersion, rollingChannel, minorChannel);

        await().ignoreExceptions().untilAsserted(() -> {
            var channels = getAvailableChannels();
            log.info("Found channels: {}", channels);

            assertThat(channels).as("Catalog must contain the rolling channel")
                    .contains(rollingChannel);
            assertThat(channels).as("Catalog must contain the minor-version channel")
                    .contains(minorChannel);
        });
    }

    @RetryTest
    void testDefaultChannel() {
        var projectVersion = getProjectVersion();
        var expectedDefault = deriveRollingChannel(projectVersion);

        await().ignoreExceptions().untilAsserted(() -> {
            var defaultChannel = getActualDefaultChannel();
            log.info("Default channel: {} (expected: {})", defaultChannel, expectedDefault);

            assertThat(defaultChannel)
                    .as("Default channel should be the rolling channel so new installs get the latest")
                    .isEqualTo(expectedDefault);
        });
    }

    @RetryTest
    void testChannelHeadsAreFromExpectedMinorStreams() {
        var projectVersion = getProjectVersion();
        var minorChannel = deriveMinorChannel(projectVersion);
        var rollingChannel = deriveRollingChannel(projectVersion);

        await().ignoreExceptions().untilAsserted(() -> {
            var channelHeads = getChannelHeads();
            var rollingHead = channelHeads.getOrDefault(rollingChannel, "NOT_FOUND");
            var minorHead = channelHeads.getOrDefault(minorChannel, "NOT_FOUND");

            log.info("Channel heads: {}={}, {}={}", rollingChannel, rollingHead, minorChannel,
                    minorHead);

            assertThat(rollingHead).as("Rolling channel head should be set")
                    .isNotEqualTo("NOT_FOUND");
            assertThat(minorHead).as("Minor channel head should be set")
                    .isNotEqualTo("NOT_FOUND");

            assertThat(rollingHead).as("Rolling channel head should be an apicurio-registry CSV")
                    .startsWith(PACKAGE_NAME + ".v");

            var expectedMinorPrefix = PACKAGE_NAME + ".v"
                    + projectVersion.toLowerCase().replaceAll("(\\d+\\.\\d+)\\..*", "$1.");
            assertThat(minorHead)
                    .as("Minor channel " + minorChannel + " head should be from the "
                            + minorChannel + " stream")
                    .startsWith(expectedMinorPrefix);
        });
    }

    @RetryTest
    void testCurrentVersionNotInOtherMinorChannels() {
        var projectVersion = getProjectVersion();
        var currentMinorChannel = deriveMinorChannel(projectVersion);
        var rollingChannel = deriveRollingChannel(projectVersion);

        await().ignoreExceptions().untilAsserted(() -> {
            var channelHeads = getChannelHeads();
            var currentMinorHead = channelHeads.get(currentMinorChannel);

            for (var entry : channelHeads.entrySet()) {
                var channelName = entry.getKey();
                var head = entry.getValue();
                if (channelName.equals(currentMinorChannel)
                        || channelName.equals(rollingChannel)) {
                    continue;
                }
                log.info("Checking channel {} (head: {}) does not share head with {} (head: {})",
                        channelName, head, currentMinorChannel, currentMinorHead);
                assertThat(head)
                        .as("Channel " + channelName + " should have a different head than "
                                + currentMinorChannel)
                        .isNotEqualTo(currentMinorHead);
            }
        });
    }

    private List<String> getAvailableChannels() throws Exception {
        if (getOlmVersion() == 0) {
            var pm = getPackageManifest(client, namespace);
            assertThat(pm).as("PackageManifest for " + PACKAGE_NAME).isNotNull();
            return getChannelNames(pm);
        }
        return getChannelsFromCatalogPod();
    }

    private String getActualDefaultChannel() throws Exception {
        if (getOlmVersion() == 0) {
            var pm = getPackageManifest(client, namespace);
            assertThat(pm).isNotNull();
            return getDefaultChannel(pm);
        }
        return getDefaultChannelFromCatalogPod();
    }

    private Map<String, String> getChannelHeads() throws Exception {
        if (getOlmVersion() == 0) {
            var pm = getPackageManifest(client, namespace);
            assertThat(pm).isNotNull();
            var heads = new java.util.HashMap<String, String>();
            for (var ch : getChannels(pm)) {
                heads.put((String) ch.get("name"), (String) ch.get("currentCSV"));
            }
            return heads;
        }
        return getChannelHeadsFromCatalogPod();
    }

    private String readCatalogContent() throws Exception {
        var catalogdUrl = "https://catalogd-service.olmv1-system.svc/catalogs/"
                + "apicurio-registry-operator-catalog/api/v1/all";
        var podName = "catalog-query-" + namespace.substring(namespace.length() - 7);

        try {
            client.pods().inNamespace(namespace).withName(podName).delete();
            Thread.sleep(2000);
        } catch (Exception e) {
            // ignore
        }

        var pod = new io.fabric8.kubernetes.api.model.PodBuilder()
                .withNewMetadata().withName(podName).withNamespace(namespace).endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .addNewContainer()
                .withName("curl")
                .withImage("registry.access.redhat.com/ubi9/ubi-minimal:latest")
                .withCommand("sh", "-c",
                        "curl -sk '" + catalogdUrl + "' 2>/dev/null || "
                                + "curl -sk 'https://catalogd-service.olmv1-system.svc/catalogs/"
                                + "apicurio-registry-operator-catalog/all' 2>/dev/null")
                .endContainer()
                .endSpec()
                .build();
        client.pods().inNamespace(namespace).resource(pod).create();

        await().atMost(java.time.Duration.ofMinutes(2)).ignoreExceptions().until(() -> {
            var p = client.pods().inNamespace(namespace).withName(podName).get();
            return p != null && ("Succeeded".equals(p.getStatus().getPhase())
                    || "Failed".equals(p.getStatus().getPhase()));
        });

        var content = client.pods().inNamespace(namespace).withName(podName).getLog();
        client.pods().inNamespace(namespace).withName(podName).delete();

        if (content == null || content.isEmpty()) {
            throw new IllegalStateException("Empty catalog content from catalogd API");
        }
        return content;
    }

    @SuppressWarnings("unchecked")
    private List<String> getChannelsFromCatalogPod() throws Exception {
        var catalog = readCatalogContent();
        var channels = new ArrayList<String>();
        for (var obj : parseCatalogObjects(catalog)) {
            if ("olm.channel".equals(obj.get("schema"))) {
                channels.add((String) obj.get("name"));
            }
        }
        return channels;
    }

    @SuppressWarnings("unchecked")
    private String getDefaultChannelFromCatalogPod() throws Exception {
        var catalog = readCatalogContent();
        for (var obj : parseCatalogObjects(catalog)) {
            if ("olm.package".equals(obj.get("schema"))) {
                return (String) obj.get("defaultChannel");
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getChannelHeadsFromCatalogPod() throws Exception {
        var catalog = readCatalogContent();
        var heads = new java.util.HashMap<String, String>();
        for (var obj : parseCatalogObjects(catalog)) {
            if ("olm.channel".equals(obj.get("schema"))) {
                var name = (String) obj.get("name");
                var entries = (List<Map<String, Object>>) obj.get("entries");
                if (entries != null && !entries.isEmpty()) {
                    heads.put(name, (String) entries.get(0).get("name"));
                }
            }
        }
        return heads;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseCatalogObjects(String catalog) throws Exception {
        var objects = new ArrayList<Map<String, Object>>();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // Try multi-document JSON (one JSON object per line/section)
        var parts = catalog.split("\\n(?=\\{)");
        for (var part : parts) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            try {
                objects.add(mapper.readValue(part, Map.class));
            } catch (Exception e) {
                // skip unparseable sections
            }
        }

        // If nothing parsed as JSON, try YAML
        if (objects.isEmpty()) {
            var yamlParts = catalog.split("(?m)^---$");
            for (var part : yamlParts) {
                part = part.trim();
                if (part.isEmpty()) {
                    continue;
                }
                try {
                    objects.add(mapper.readValue(part, Map.class));
                } catch (Exception e) {
                    // skip
                }
            }
        }

        return objects;
    }
}
