package io.apicurio.registry.operator.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.apicurio.registry.operator.it.CatalogInfo.ChannelEntry;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.apicurio.registry.operator.it.CatalogInfo.extractVersionString;
import static io.apicurio.registry.operator.it.CatalogInfo.parseVersion;
import static io.apicurio.registry.operator.it.OLMTestUtils.*;

/**
 * Queries the live OLM catalog by reading the File-Based Catalog (FBC) content directly from the
 * catalog pod. Results are cached so the catalog is queried only once per test run.
 * <p>
 * This replaces the PackageManifest-based discovery, which was unreliable because it merges data
 * from all catalog sources on the cluster and strips productized version suffixes.
 * <p>
 * The FBC content is read by exec'ing into the catalog pod and cat'ing the catalog file. Two
 * path conventions are supported:
 * <ul>
 *   <li>IIB (downstream): {@code /configs/<package>/catalog.json}</li>
 *   <li>Upstream: {@code /configs/index.yaml}</li>
 * </ul>
 */
public class CatalogDiscovery {

    private static final Logger log = LoggerFactory.getLogger(CatalogDiscovery.class);

    private static final String CATALOG_POD_PREFIX = "apicurio-registry-operator-catalog";

    private static CatalogDiscovery instance;

    @Getter
    private CatalogInfo catalogInfo;

    private CatalogDiscovery(CatalogInfo catalogInfo) {
        this.catalogInfo = catalogInfo;
    }

    /**
     * Returns the singleton instance, performing discovery on first call. Discovery creates a temporary
     * namespace, deploys a CatalogSource, reads the FBC content from the catalog pod, and cleans up.
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

            var podName = findCatalogPodName(client, namespace);
            log.info("Catalog pod found: {}", podName);

            var fbcContent = readFBCFromPod(client, namespace, podName);
            log.info("FBC content read from catalog pod ({} bytes)", fbcContent.length());

            var info = parseFBC(fbcContent);
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

    private static String findCatalogPodName(KubernetesClient client, String namespace) {
        var pods = client.pods().inNamespace(namespace).list().getItems();
        for (var pod : pods) {
            var name = pod.getMetadata().getName();
            if (name.startsWith(CATALOG_POD_PREFIX)) {
                return name;
            }
        }
        throw new IllegalStateException("No catalog pod found with prefix '" + CATALOG_POD_PREFIX
                + "' in namespace " + namespace);
    }

    private static String readFBCFromPod(KubernetesClient client, String namespace, String podName) {
        var paths = List.of(
                "/configs/" + PACKAGE_NAME + "/catalog.json", // IIB (downstream)
                "/configs/index.yaml" // Upstream
        );

        for (var path : paths) {
            log.info("Trying FBC path: {}", path);
            var content = execCat(client, namespace, podName, path);
            if (content != null && !content.isBlank()) {
                log.info("Found FBC at path: {} ({} bytes)", path, content.length());
                return content;
            }
        }

        var listing = execCat(client, namespace, podName, null);
        throw new IllegalStateException(
                "Could not read FBC content from catalog pod " + podName
                        + ". Tried paths: " + paths
                        + ". This catalog image may not use the File-Based Catalog format."
                        + (listing != null ? " /configs listing: " + listing : ""));
    }

    private static String execCat(KubernetesClient client, String namespace, String podName,
            String path) {
        try {
            var out = new ByteArrayOutputStream();
            var err = new ByteArrayOutputStream();
            String[] cmd;
            if (path != null) {
                cmd = new String[] { "cat", path };
            } else {
                cmd = new String[] { "ls", "-la", "/configs/" };
            }
            log.debug("Exec in pod {}: {}", podName, String.join(" ", cmd));
            try (ExecWatch exec = client.pods().inNamespace(namespace).withName(podName)
                    .redirectingOutput()
                    .redirectingError()
                    .exec(cmd)) {
                var output = exec.getOutput();
                var error = exec.getError();
                if (output != null) {
                    output.transferTo(out);
                }
                if (error != null) {
                    error.transferTo(err);
                }
                exec.exitCode().get(30, TimeUnit.SECONDS);
            }
            var errStr = err.toString().trim();
            if (!errStr.isEmpty()) {
                log.debug("Exec stderr: {}", errStr);
            }
            var result = out.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            log.debug("Exec failed for path {}: {}", path, e.getMessage());
            return null;
        }
    }

    static CatalogInfo parseFBC(String fbcContent) {
        var objects = new ArrayList<JsonNode>();

        var isYaml = fbcContent.stripLeading().startsWith("---") || !fbcContent.stripLeading().startsWith("{");
        if (isYaml) {
            log.info("FBC content detected as YAML, parsing with YAML parser");
            var yamlMapper = new ObjectMapper(new YAMLFactory());
            for (var part : fbcContent.split("(?m)^---$")) {
                var trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    objects.add(yamlMapper.readTree(trimmed));
                } catch (Exception e) {
                    log.warn("Failed to parse YAML FBC fragment ({} chars): {}",
                            trimmed.length(), e.getMessage());
                }
            }
        } else {
            log.info("FBC content detected as JSON, parsing with JSON parser");
            var jsonMapper = new ObjectMapper();
            // FBC JSON is multi-document: multiple JSON objects concatenated (not a JSON array).
            for (var part : fbcContent.split("(?m)^(?=\\{)")) {
                var trimmed = part.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    objects.add(jsonMapper.readTree(trimmed));
                } catch (Exception e) {
                    log.warn("Failed to parse JSON FBC fragment ({} chars): {}",
                            trimmed.length(), e.getMessage());
                }
            }
        }

        log.info("Parsed {} FBC objects from catalog content", objects.size());
        if (objects.isEmpty()) {
            throw new IllegalStateException(
                    "No FBC objects found in catalog content. The content may not be valid FBC format.");
        }

        String defaultChannel = null;
        var channels = new LinkedHashMap<String, List<ChannelEntry>>();

        for (var obj : objects) {
            var schema = obj.path("schema").asText("");
            switch (schema) {
                case "olm.package" -> {
                    defaultChannel = obj.path("defaultChannel").asText(null);
                    var packageName = obj.path("name").asText("");
                    log.info("FBC package: {} defaultChannel={}", packageName, defaultChannel);
                    if (!PACKAGE_NAME.equals(packageName)) {
                        log.warn("FBC package name '{}' does not match expected '{}'",
                                packageName, PACKAGE_NAME);
                    }
                }
                case "olm.channel" -> {
                    var channelName = obj.path("name").asText("");
                    var entries = new ArrayList<ChannelEntry>();
                    var replacedNames = new HashSet<String>();
                    var entriesNode = obj.get("entries");
                    if (entriesNode != null && entriesNode.isArray()) {
                        for (var entryNode : entriesNode) {
                            var csvName = entryNode.path("name").asText("");
                            var replaces = entryNode.path("replaces").asText(null);
                            if (replaces != null) {
                                replacedNames.add(replaces);
                            }
                            var versionStr = extractVersionString(csvName);
                            try {
                                entries.add(new ChannelEntry(csvName, parseVersion(versionStr)));
                            } catch (IllegalArgumentException e) {
                                log.warn("Skipping entry with unparseable version: {} ({})",
                                        csvName, e.getMessage());
                            }
                        }
                    }
                    // Order entries with the channel head first. The head is the entry that
                    // no other entry replaces and is itself part of the replaces chain.
                    entries.sort((a, b) -> {
                        var aIsHead = !replacedNames.contains(a.getCsvName());
                        var bIsHead = !replacedNames.contains(b.getCsvName());
                        if (aIsHead != bIsHead) {
                            return aIsHead ? -1 : 1;
                        }
                        return b.getVersion().compareTo(a.getVersion());
                    });
                    channels.put(channelName, entries);
                    if (!entries.isEmpty()) {
                        log.info("FBC channel {}: {} entries, head={}",
                                channelName, entries.size(), entries.get(0).getCsvName());
                    }
                }
                case "olm.bundle" -> {
                    // Bundle objects contain the CSV content — not needed for discovery
                }
                default -> log.debug("Ignoring FBC object with schema: {}", schema);
            }
        }

        if (channels.isEmpty()) {
            throw new IllegalStateException(
                    "No channels found in FBC content. Parsed " + objects.size()
                            + " objects but none had schema 'olm.channel'.");
        }

        return new CatalogInfo(channels, defaultChannel);
    }
}
