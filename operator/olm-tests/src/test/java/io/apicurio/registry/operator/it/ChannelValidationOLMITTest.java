package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.utils.RetryTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.apicurio.registry.operator.Tags.OLM;
import static io.apicurio.registry.operator.it.OLMTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Validates OLM catalog channel configuration.
 * <p>
 * Under OLM v0, uses the PackageManifest API. Under OLM v1, reads File-Based Catalog (FBC) content
 * from catalogd (via {@link CatalogdClient}) and parses it via {@link CatalogDiscovery#parseFBC} into a
 * {@link CatalogInfo}, since PackageManifest may return data from other catalog sources on the cluster.
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

    // ---- Data access methods ----
    // TODO(#9677): When OLM v1 upgrade tests are added, the v0/v1 catalog data source branching
    //  below (and in CatalogDiscovery/UpgradeOLMITTest) should be unified behind a strategy
    //  interface (e.g., CatalogDataSource) with separate PM, FBC, and catalogd implementations.

    private List<String> getAvailableChannels() throws Exception {
        if (getOlmVersion() == 0) {
            var pm = getPackageManifest(client, namespace);
            assertThat(pm).as("PackageManifest for " + PACKAGE_NAME).isNotNull();
            return getChannelNames(pm);
        }
        return getCatalogInfoV1().getChannels().keySet().stream().toList();
    }

    private String getActualDefaultChannel() throws Exception {
        if (getOlmVersion() == 0) {
            var pm = getPackageManifest(client, namespace);
            assertThat(pm).isNotNull();
            return getDefaultChannel(pm);
        }
        return getCatalogInfoV1().getDefaultChannel();
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
        var info = getCatalogInfoV1();
        return info.getChannels().entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0).getCsvName()));
    }

    // ---- OLM v1 catalogd access ----

    /**
     * Reads FBC content from catalogd (via {@link CatalogdClient}) and parses it with
     * {@link CatalogDiscovery#parseFBC}, the same parser {@code CatalogDiscovery} uses for OLM v0's
     * exec-from-pod FBC content, so channel/version parsing logic lives in exactly one place. The
     * base URL is discovered from the ClusterCatalog's {@code status.urls.base} field.
     */
    private CatalogInfo getCatalogInfoV1() throws Exception {
        var content = CatalogdClient.readCatalogContent(client, namespace, CATALOG_NAME);
        return CatalogDiscovery.parseFBC(content);
    }
}
