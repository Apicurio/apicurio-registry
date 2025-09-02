package io.apicurio.registry.cli.services;

import io.vertx.core.http.HttpMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.ConfigProvider;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Service for checking and managing CLI updates.
 * Uses MicroProfile Config for configuration management.
 */
public class Update {

    private static final Logger log = LogManager.getRootLogger();

    private static Update instance;

    public static synchronized Update getInstance() {
        if (instance == null) {
            instance = new Update();
        }
        return instance;
    }

    private Update() {
    }

    public String getLatestVersion() {
        try {
            String updateUrl = ConfigProvider.getConfig().getValue("acr.update.repo.url", String.class);
            if (updateUrl == null || updateUrl.isBlank()) {
                throw new RuntimeException("Update repository URL is not configured.");
            }
            String metadataUri = updateUrl.endsWith("/") ? updateUrl + "maven-metadata.xml" : updateUrl + "/maven-metadata.xml";

            // Parse the URI to get host, port, and path
            URI uri = URI.create(metadataUri);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80);
            String path = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

            log.debug("Downloading metadata from: {}", metadataUri);

            // Use Vertx HttpClient to download the metadata XML file
            var httpClient = Client.getInstance().getHttpClient();
            CompletableFuture<String> future = new CompletableFuture<>();

            var requestOptions = new io.vertx.core.http.RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setPort(port)
                    .setHost(host)
                    .setURI(path)
                    .setSsl(uri.getScheme().equals("https"));

            httpClient.request(requestOptions)
                    .onSuccess(clientReq -> {
                        clientReq.send()
                                .onSuccess(response -> {
                                    if (response.statusCode() != 200) {
                                        log.warn("Failed to fetch metadata. Status code: {}", response.statusCode());
                                        future.complete(null);
                                        return;
                                    }

                                    response.body()
                                            .onSuccess(buffer -> {
                                                try {
                                                    String xmlContent = buffer.toString();
                                                    log.debug("Metadata XML content received");

                                                    // Parse the XML and extract the latest version
                                                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                                                    DocumentBuilder builder = factory.newDocumentBuilder();
                                                    Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes()));

                                                    // Extract the <latest> tag from <versioning>
                                                    NodeList latestNodes = doc.getElementsByTagName("latest");
                                                    if (latestNodes.getLength() > 0) {
                                                        String latestVersion = latestNodes.item(0).getTextContent();
                                                        log.debug("Latest version available: {}", latestVersion);
                                                        future.complete(latestVersion);
                                                    } else {
                                                        log.warn("No <latest> tag found in metadata XML");
                                                        future.complete(null);
                                                    }
                                                } catch (Exception e) {
                                                    log.error("Error parsing XML", e);
                                                    future.completeExceptionally(e);
                                                }
                                            })
                                            .onFailure(err -> {
                                                log.error("Error reading response body", err);
                                                future.completeExceptionally(err);
                                            });
                                })
                                .onFailure(err -> {
                                    log.error("Error sending request", err);
                                    future.completeExceptionally(err);
                                });
                    })
                    .onFailure(err -> {
                        log.error("Error creating request", err);
                        future.completeExceptionally(err);
                    });

            // Wait for the async operation to complete (with timeout)
            return future.get(30, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Error checking for latest version", e);
            return null;
        }
    }

    public Path downloadVersion(String version, Path targetDir) {
        try {
            String updateUrl = ConfigProvider.getConfig().getValue("acr.update.repo.url", String.class);
            if (updateUrl == null || updateUrl.isBlank()) {
                throw new RuntimeException("Update repository URL is not configured.");
            }
            var pathSegment = "%1$s/apicurio-registry-cli-%1$s.zip".formatted(version);
            String fileUri = updateUrl.endsWith("/") ? updateUrl + pathSegment : updateUrl + "/" + pathSegment;

            // Parse the URI to get host, port, and path
            URI uri = URI.create(fileUri);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80);
            String uriPath = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");

            log.info("Downloading version {} from: {}", version, fileUri);

            // Ensure target directory exists
            if (!targetDir.toFile().exists()) {
                targetDir.toFile().mkdirs();
            }

            // Create the target file path
            Path targetFile = targetDir.resolve("apicurio-registry-cli-" + version + ".zip");

            // Use Vertx HttpClient to download the file
            var httpClient = Client.getInstance().getHttpClient();
            CompletableFuture<Path> future = new CompletableFuture<>();

            var requestOptions = new io.vertx.core.http.RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setPort(port)
                    .setHost(host)
                    .setURI(uriPath)
                    .setSsl(uri.getScheme().equals("https"));

            httpClient.request(requestOptions)
                    .onSuccess(clientReq -> {
                        clientReq.send()
                                .onSuccess(response -> {
                                    if (response.statusCode() != 200) {
                                        log.error("Failed to download file. Status code: {}", response.statusCode());
                                        future.completeExceptionally(new RuntimeException("HTTP " + response.statusCode()));
                                        return;
                                    }

                                    response.body()
                                            .onSuccess(buffer -> {
                                                try {
                                                    // Write the buffer to the target file
                                                    java.nio.file.Files.write(targetFile, buffer.getBytes());
                                                    log.info("Successfully downloaded file to: {}", targetFile);
                                                    future.complete(targetFile);
                                                } catch (Exception e) {
                                                    log.error("Error writing file to disk", e);
                                                    future.completeExceptionally(e);
                                                }
                                            })
                                            .onFailure(err -> {
                                                log.error("Error reading response body", err);
                                                future.completeExceptionally(err);
                                            });
                                })
                                .onFailure(err -> {
                                    log.error("Error sending request", err);
                                    future.completeExceptionally(err);
                                });
                    })
                    .onFailure(err -> {
                        log.error("Error creating request", err);
                        future.completeExceptionally(err);
                    });

            // Wait for the async operation to complete (with timeout)
            return future.get(60, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Error downloading version", e);
            return null;
        }
    }
}
