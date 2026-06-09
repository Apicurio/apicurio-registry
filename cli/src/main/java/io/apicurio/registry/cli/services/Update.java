package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.PlatformUtils;
import io.vertx.core.http.HttpMethod;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;

@ApplicationScoped
public class Update {

    private static final Logger log = Logger.getLogger(Update.class);

    @Inject
    Client client;

    @Inject
    Config config;

    private static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private int getTimeoutSeconds() {
        try {
            var value = config.read().getConfig().get("update.timeout-seconds");
            return value != null ? Integer.parseInt(value) : DEFAULT_TIMEOUT_SECONDS;
        } catch (Exception e) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }

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

        var configModel = config.read();
        configModel.getConfig().put("internal.update.last-check", Instant.now().toString());
        config.write(configModel);

        return new UpdateCheckResult(currentVersion, List.copyOf(parsed));
    }

    List<CliVersion> fetchAvailableVersions() {
        var metadataUrl = getMetadataUrl();
        var timeout = getTimeoutSeconds();
        var bytes = fetchBytes(metadataUrl, timeout);
        if (bytes == null) {
            throw new CliException("Could not fetch update metadata from " + metadataUrl);
        }
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(bytes));

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
        } catch (CliException e) {
            throw e;
        } catch (Exception e) {
            throw new CliException("Error parsing update metadata from " + metadataUrl, e, APPLICATION_ERROR_RETURN_CODE);
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
        var content = fetchBytes(fileUri, getTimeoutSeconds());
        if (content == null) {
            throw new CliException("Failed to download " + fileUri);
        }
        try {
            Files.write(targetFile, content);
            log.infof("Successfully downloaded file to: %s", targetFile);
            return targetFile;
        } catch (Exception e) {
            throw new CliException("Error writing downloaded file to: " + targetFile, e, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    private String getRepoUrl() {
        String url = ConfigProvider.getConfig().getValue("internal.update.repo-url", String.class);
        if (url == null || url.isBlank()) {
            throw new CliException("Update repository URL is not configured.");
        }
        return url;
    }

    private String getMetadataUrl() {
        var repoUrl = getRepoUrl();
        return repoUrl.endsWith("/") ? repoUrl + "maven-metadata.xml" : repoUrl + "/maven-metadata.xml";
    }

    private byte[] fetchBytes(String url, int timeoutSeconds) {
        try {
            URI uri = URI.create(url);
            var httpClient = client.getHttpClient();
            CompletableFuture<byte[]> future = new CompletableFuture<>();

            boolean ssl = "https".equals(uri.getScheme());
            int port = uri.getPort() != -1 ? uri.getPort() : (ssl ? 443 : 80);
            var requestOptions = new io.vertx.core.http.RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setPort(port)
                    .setHost(uri.getHost())
                    .setURI(uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
                    .setSsl(ssl);

            log.debugf("Fetching: %s", url);

            httpClient.request(requestOptions)
                    .onSuccess(req -> req.send()
                            .onSuccess(response -> {
                                if (response.statusCode() != 200) {
                                    log.warnf("HTTP %d from %s", response.statusCode(), url);
                                    future.completeExceptionally(new CliException("HTTP " + response.statusCode()));
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
        } catch (TimeoutException _ignored) {
            log.errorf("Request timed out after %ds: %s", timeoutSeconds, url);
            config.getStdErr().print("""
                    Request timed out. If you have a slow connection, increase the timeout:
                      acr config set update.timeout-seconds=120
                    """);
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ex) {
            log.errorf("Error fetching %s: %s", url, ex.getMessage());
            return null;
        }
    }
}
