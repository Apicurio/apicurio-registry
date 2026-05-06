package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.utils.PlatformUtils;
import io.vertx.core.http.HttpMethod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@ApplicationScoped
public class Update {

    private static final Logger log = Logger.getLogger(Update.class);

    @Inject
    Client client;

    public UpdateCheckResult checkForUpdates(CliVersion currentVersion) {
        var allVersions = fetchAvailableVersions();
        if (allVersions == null || allVersions.isEmpty()) {
            return new UpdateCheckResult(currentVersion, List.of());
        }

        // Filter parsed versions by productized/community compatibility, keep only newer
        var newer = allVersions.stream()
                .filter(CliVersion::isParsed)
                .filter(v -> v.isProductized() == currentVersion.isProductized())
                .filter(v -> v.isNewerThan(currentVersion))
                .toList();

        // Group by major.minor series, pick the latest in each series
        var parsed = newer.stream()
                .collect(Collectors.groupingBy(
                        v -> v.major() + "." + v.minor(),
                        Collectors.maxBy(CliVersion.COMPARATOR)))
                .values().stream()
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .sorted(CliVersion.COMPARATOR)
                .collect(Collectors.toCollection(ArrayList::new));

        // Include unparsed versions as ambiguous candidates
        allVersions.stream()
                .filter(v -> !v.isParsed())
                .forEach(parsed::add);

        return new UpdateCheckResult(currentVersion, List.copyOf(parsed));
    }

    List<CliVersion> fetchAvailableVersions() {
        var xml = fetchContent(getMetadataUrl(), 30);
        if (xml == null) {
            return List.of();
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));

            NodeList versionNodes = doc.getElementsByTagName("version");
            List<CliVersion> versions = new ArrayList<>();
            for (int i = 0; i < versionNodes.getLength(); i++) {
                var text = versionNodes.item(i).getTextContent();
                if (text != null && !text.isBlank()) {
                    versions.add(CliVersion.parse(text));
                }
            }
            var unparsed = versions.stream().filter(v -> !v.isParsed()).count();
            log.debugf("Found %d versions in metadata (%d unparsed)", versions.size(), unparsed);
            return versions;
        } catch (Exception e) {
            log.error("Error parsing maven-metadata.xml", e);
            return List.of();
        }
    }

    public Path downloadVersion(String version, Path targetDir) {
        var platform = PlatformUtils.detectPlatformClassifier();
        log.debugf("Detected platform: %s", platform);
        var fileName = "apicurio-registry-cli-%s-%s.zip".formatted(version, platform);
        var pathSegment = "%s/%s".formatted(version, fileName);
        var repoUrl = getRepoUrl();
        var fileUri = repoUrl.endsWith("/") ? repoUrl + pathSegment : repoUrl + "/" + pathSegment;

        log.infof("Downloading version %s from: %s", version, fileUri);

        if (!targetDir.toFile().exists()) {
            targetDir.toFile().mkdirs();
        }

        Path targetFile = targetDir.resolve(fileName);
        var content = fetchBytes(fileUri, 60);
        if (content == null) {
            return null;
        }
        try {
            java.nio.file.Files.write(targetFile, content);
            log.infof("Successfully downloaded file to: %s", targetFile);
            return targetFile;
        } catch (Exception e) {
            log.errorf(e, "Error writing downloaded file to: %s", targetFile);
            return null;
        }
    }

    private String getRepoUrl() {
        String url = ConfigProvider.getConfig().getValue("internal.update.repo-url", String.class);
        if (url == null || url.isBlank()) {
            throw new RuntimeException("Update repository URL is not configured.");
        }
        return url;
    }

    private String getMetadataUrl() {
        var repoUrl = getRepoUrl();
        return repoUrl.endsWith("/") ? repoUrl + "maven-metadata.xml" : repoUrl + "/maven-metadata.xml";
    }

    private String fetchContent(String url, int timeoutSeconds) {
        try {
            URI uri = URI.create(url);
            var httpClient = client.getHttpClient();
            CompletableFuture<String> future = new CompletableFuture<>();

            var requestOptions = new io.vertx.core.http.RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setPort(uri.getPort() != -1 ? uri.getPort() : (Objects.equals(uri.getScheme(), "https") ? 443 : 80))
                    .setHost(uri.getHost())
                    .setURI(uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
                    .setSsl(Objects.equals(uri.getScheme(), "https"));

            log.debugf("Fetching: %s", url);

            httpClient.request(requestOptions)
                    .onSuccess(req -> req.send()
                            .onSuccess(response -> {
                                if (response.statusCode() != 200) {
                                    log.warnf("HTTP %d from %s", response.statusCode(), url);
                                    future.complete(null);
                                    return;
                                }
                                response.body()
                                        .onSuccess(buffer -> future.complete(buffer.toString()))
                                        .onFailure(err -> {
                                            log.error("Error reading response body", err);
                                            future.completeExceptionally(err);
                                        });
                            })
                            .onFailure(err -> {
                                log.error("Error sending request", err);
                                future.completeExceptionally(err);
                            }))
                    .onFailure(err -> {
                        log.error("Error creating request", err);
                        future.completeExceptionally(err);
                    });

            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.errorf("Error fetching %s: %s", url, e.getMessage());
            return null;
        }
    }

    private byte[] fetchBytes(String url, int timeoutSeconds) {
        try {
            URI uri = URI.create(url);
            var httpClient = client.getHttpClient();
            CompletableFuture<byte[]> future = new CompletableFuture<>();

            var requestOptions = new io.vertx.core.http.RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setPort(uri.getPort() != -1 ? uri.getPort() : (Objects.equals(uri.getScheme(), "https") ? 443 : 80))
                    .setHost(uri.getHost())
                    .setURI(uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
                    .setSsl(Objects.equals(uri.getScheme(), "https"));

            log.debugf("Downloading: %s", url);

            httpClient.request(requestOptions)
                    .onSuccess(req -> req.send()
                            .onSuccess(response -> {
                                if (response.statusCode() != 200) {
                                    log.errorf("HTTP %d from %s", response.statusCode(), url);
                                    future.completeExceptionally(new RuntimeException("HTTP " + response.statusCode()));
                                    return;
                                }
                                response.body()
                                        .onSuccess(buffer -> future.complete(buffer.getBytes()))
                                        .onFailure(err -> {
                                            log.error("Error reading response body", err);
                                            future.completeExceptionally(err);
                                        });
                            })
                            .onFailure(err -> {
                                log.error("Error sending request", err);
                                future.completeExceptionally(err);
                            }))
                    .onFailure(err -> {
                        log.error("Error creating request", err);
                        future.completeExceptionally(err);
                    });

            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.errorf("Error downloading %s: %s", url, e.getMessage());
            return null;
        }
    }
}
