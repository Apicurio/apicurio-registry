package io.apicurio.registry.cli.services;

import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.config.Config;
import io.apicurio.registry.cli.utils.PlatformUtils;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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

    /**
     * Opt-in escape hatch for private/custom repositories that don't publish {@code .sha256}
     * sidecar files alongside the archive. Defaults to {@code false} so verification is
     * fail-closed unless explicitly disabled.
     */
    private boolean isSkipChecksumVerification() {
        try {
            var value = config.read().getConfig().get("update.skip-checksum-verification");
            return "true".equalsIgnoreCase(value);
        } catch (Exception e) {
            return false;
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
                .filter(Optional::isPresent)
                .map(Optional::get)
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
        verifyChecksum(fileUri, content);
        try {
            Files.write(targetFile, content);
            log.infof("Successfully downloaded file to: %s", targetFile);
            return targetFile;
        } catch (Exception e) {
            throw new CliException("Error writing downloaded file to: " + targetFile, e, APPLICATION_ERROR_RETURN_CODE);
        }
    }

    /**
     * Verifies the SHA-256 checksum of the downloaded content against the {@code .sha256} file
     * published alongside it in the repository, failing the update if they do not match.
     * Skipped entirely when {@code update.skip-checksum-verification} is enabled, for custom
     * repositories that don't publish checksum sidecar files.
     */
    private void verifyChecksum(String fileUri, byte[] content) {
        if (isSkipChecksumVerification()) {
            log.warn("Download integrity check skipped (update.skip-checksum-verification=true). "
                    + "The downloaded archive will not be verified against a checksum.");
            return;
        }
        var checksumUri = fileUri + ".sha256";
        log.debugf("Verifying download integrity using: %s", checksumUri);
        // fetchBytes returns null (it does not throw) on any failure, so a missing or unreachable
        // checksum is turned into a hard failure here to keep verification fail-closed.
        var checksumBytes = fetchBytes(checksumUri, getTimeoutSeconds());
        if (checksumBytes == null) {
            throw new CliException("Could not download the checksum from " + checksumUri
                    + " to verify the integrity of the update.");
        }
        var expected = parseChecksum(checksumBytes);
        var actual = sha256Hex(content);
        if (!expected.equals(actual)) {
            throw new CliException("Checksum verification failed for " + fileUri
                    + ". Expected SHA-256 " + expected + " but got " + actual
                    + ". The download may be corrupted; please try again.");
        }
        log.debugf("Checksum verified: %s", actual);
    }

    /**
     * Extracts the hex digest from a Maven checksum file, which contains the digest optionally
     * followed by whitespace and the file name.
     */
    static String parseChecksum(byte[] checksumBytes) {
        var text = new String(checksumBytes, StandardCharsets.UTF_8).trim();
        var end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(0, end).toLowerCase(Locale.ROOT);
    }

    /**
     * Computes the lowercase hex SHA-256 digest of the given content.
     */
    static String sha256Hex(byte[] content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new CliException("SHA-256 algorithm is not available.", e, APPLICATION_ERROR_RETURN_CODE);
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
            var requestOptions = new RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setPort(port)
                    .setHost(uri.getHost())
                    .setURI(uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
                    .setSsl(ssl)
                    .setFollowRedirects(true);

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
