package io.apicurio.registry.operator.it;

import io.apicurio.registry.operator.it.CatalogInfo.ChannelEntry;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static io.apicurio.registry.operator.it.CatalogInfo.extractVersionString;
import static io.apicurio.registry.operator.it.CatalogInfo.parseVersion;
import static io.apicurio.registry.operator.it.OLMTestUtils.*;
import static org.awaitility.Awaitility.await;

/**
 * Queries the live OLM catalog (via PackageManifest) to discover channels, CSV names, and upgrade paths.
 * Results are cached so the catalog is queried only once per test run.
 * <p>
 * This replaces hardcoded version strings and template file reads, making upgrade tests work with both
 * upstream catalogs (built from catalog.template.yaml) and downstream IIB images (where CSV names have
 * productized suffixes like -r1).
 */
public class CatalogDiscovery {

    private static final Logger log = LoggerFactory.getLogger(CatalogDiscovery.class);

    private static CatalogDiscovery instance;

    @Getter
    private CatalogInfo catalogInfo;

    private CatalogDiscovery(CatalogInfo catalogInfo) {
        this.catalogInfo = catalogInfo;
    }

    /**
     * Returns the singleton instance, performing discovery on first call. Discovery creates a temporary
     * namespace, deploys a CatalogSource, waits for the PackageManifest, extracts channel data, and
     * cleans up.
     */
    public static synchronized CatalogDiscovery getInstance(KubernetesClient client) throws Exception {
        if (instance != null) {
            log.info("Using cached catalog discovery results");
            return instance;
        }

        var namespace = "catalog-discovery-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Starting catalog discovery in temporary namespace {}", namespace);

        try {
            ITBase.createNamespace(client, namespace);

            createResource(client, namespace, "olmv0/catalog-source.yaml");
            waitForCatalogPodReady(client, namespace);

            var info = queryPackageManifest(client, namespace);
            instance = new CatalogDiscovery(info);

            log.info("Catalog discovery complete:");
            log.info("  Default channel: {}", info.getDefaultChannel());
            for (var entry : info.getChannels().entrySet()) {
                var entries = entry.getValue();
                log.info("  Channel {}: {} entries, head={}",
                        entry.getKey(), entries.size(),
                        entries.isEmpty() ? "(empty)" : entries.get(0).getCsvName());
            }

            return instance;
        } finally {
            try {
                deleteResourceQuietly(client, namespace, "olmv0/catalog-source.yaml");
                client.namespaces().withName(namespace).delete();
            } catch (Exception e) {
                log.warn("Cleanup of discovery namespace {} failed: {}", namespace, e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static CatalogInfo queryPackageManifest(KubernetesClient client, String namespace) {
        var pm = new GenericKubernetesResource[1];
        await().ignoreExceptions().untilAsserted(() -> {
            pm[0] = getPackageManifest(client, namespace);
            if (pm[0] == null) {
                throw new IllegalStateException("PackageManifest not yet available");
            }
        });

        var channels = new LinkedHashMap<String, List<ChannelEntry>>();
        var rawChannels = (Collection<Map<String, Object>>) pm[0].get("status", "channels");
        for (var ch : rawChannels) {
            var name = (String) ch.get("name");
            var currentCSV = (String) ch.get("currentCSV");

            var entries = new ArrayList<ChannelEntry>();
            var rawEntries = (Collection<Map<String, Object>>) ch.get("entries");
            if (rawEntries != null && !rawEntries.isEmpty()) {
                for (var re : rawEntries) {
                    var csvName = (String) re.get("name");
                    var versionStr = (String) re.get("version");
                    var versionRaw = versionStr != null ? versionStr : extractVersionString(csvName);
                    entries.add(new ChannelEntry(csvName, parseVersion(versionRaw)));
                }

                if (!entries.get(0).getCsvName().equals(currentCSV)) {
                    log.warn("Channel {} entries are not head-first: first entry is {} but "
                                    + "currentCSV is {}. Reordering.", name,
                            entries.get(0).getCsvName(), currentCSV);
                    entries.sort((a, b) -> {
                        if (a.getCsvName().equals(currentCSV)) return -1;
                        if (b.getCsvName().equals(currentCSV)) return 1;
                        return b.getVersion().compareTo(a.getVersion());
                    });
                }
            } else {
                entries.add(new ChannelEntry(currentCSV,
                        parseVersion(extractVersionString(currentCSV))));
                log.warn("Channel {} has no entries in PackageManifest, using head only: {}",
                        name, currentCSV);
            }

            channels.put(name, entries);
        }

        var defaultChannel = (String) pm[0].get("status", "defaultChannel");

        return new CatalogInfo(channels, defaultChannel);
    }
}
