package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.RetryTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.Tags.OLM;
import static io.apicurio.registry.operator.it.OLMTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Validates OLM catalog channel configuration.
 * <p>
 * Under OLM v0, uses the PackageManifest API. Under OLM v1, reads File-Based Catalog (FBC) content
 * directly from catalogd (via {@link CatalogdClient}) since PackageManifest may return data from other
 * catalog sources on the cluster.
 */
@QuarkusTest
@Tag(OLM)
public class ChannelValidationOLMITTest extends OLMITBase {

    private static final Logger log = LoggerFactory.getLogger(ChannelValidationOLMITTest.class);

    @Override
    @AfterEach
    public void afterEach() {
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

    /**
     * Reads FBC content from catalogd (via {@link CatalogdClient}) and parses it with
     * {@link CatalogDiscovery#parseFBC}, the same parser {@code CatalogDiscovery} uses for OLM v0's
     * exec-from-pod FBC content, so channel/version parsing logic lives in exactly one place.
     */
    private CatalogInfo queryCatalogd() throws Exception {
        var content = CatalogdClient.readCatalogContent(client, namespace, CATALOG_NAME);
        return CatalogDiscovery.parseFBC(content);
    }

    private List<String> getChannelsFromCatalogPod() throws Exception {
        return List.copyOf(queryCatalogd().getChannels().keySet());
    }

    private String getDefaultChannelFromCatalogPod() throws Exception {
        return queryCatalogd().getDefaultChannel();
    }

    private Map<String, String> getChannelHeadsFromCatalogPod() throws Exception {
        var info = queryCatalogd();
        var heads = new HashMap<String, String>();
        for (var channelName : info.getChannels().keySet()) {
            var headCsv = info.getChannelHeadCSV(channelName);
            if (headCsv != null) {
                heads.put(channelName, headCsv);
            }
        }
        return heads;
    }
}
