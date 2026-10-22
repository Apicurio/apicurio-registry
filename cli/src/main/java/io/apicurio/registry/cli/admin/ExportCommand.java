package io.apicurio.registry.cli.admin;

import io.apicurio.registry.cli.common.AbstractCommand;
import io.apicurio.registry.cli.common.CliException;
import io.apicurio.registry.cli.utils.OutputBuffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.logging.Logger;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static io.apicurio.registry.cli.common.CliException.APPLICATION_ERROR_RETURN_CODE;
import static io.apicurio.registry.cli.common.CliException.VALIDATION_ERROR_RETURN_CODE;

@Command(
        name = "export",
        description = "Export registry data to a ZIP file"
)
public class ExportCommand extends AbstractCommand {

    private static final Logger log = Logger.getLogger(ExportCommand.class);
    private static final int DOWNLOAD_TIMEOUT_SECONDS = 300;
    private static final int HTTP_OK = 200;
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;
    private static final String HTTPS_SCHEME = "https";

    @Option(
            names = {"-f", "--file"},
            required = true,
            description = "Output file path for the exported ZIP archive."
    )
    private String file;

    @Option(
            names = {"-g", "--group"},
            description = "Export only data belonging to this group."
    )
    private String groupId;

    @Override
    public void run(final OutputBuffer output) throws Exception {
        final var outputPath = Path.of(file);
        final var parentDir = outputPath.getParent();
        if (parentDir != null && !Files.isDirectory(parentDir)) {
            throw new CliException("Parent directory does not exist: " + parentDir,
                    VALIDATION_ERROR_RETURN_CODE);
        }

        final var registryClient = client.getRegistryClient();

        final var downloadRef = registryClient.admin().export().get(r -> {
            r.queryParameters.forBrowser = true;
            r.queryParameters.groupId = groupId;
        });

        if (downloadRef == null || downloadRef.getHref() == null) {
            throw new CliException("Server did not return a download reference.",
                    APPLICATION_ERROR_RETURN_CODE);
        }

        final var baseUri = resolveBaseUri();
        final var downloadUrl = baseUri.resolve(downloadRef.getHref());

        downloadFile(downloadUrl, outputPath);

        output.writeStdOutChunk(out ->
                out.append("Registry data exported to '").append(file).append("'.\n"));
    }

    private URI resolveBaseUri() {
        final var configModel = config.read();
        final var contextName = configModel.getCurrentContext();
        final var context = configModel.getContext().get(contextName);
        final var registryUrl = context.getRegistryUrl();
        final var suffix = "/apis/registry/v3";
        final var idx = registryUrl.indexOf(suffix);
        if (idx >= 0) {
            return URI.create(registryUrl.substring(0, idx));
        }
        return URI.create(registryUrl);
    }

    private void downloadFile(final URI url, final Path outputPath) {
        log.debugf("Downloading export from: %s", url);

        try {
            final var httpClient = client.getHttpClient();
            final var future = new CompletableFuture<byte[]>();

            final boolean ssl = HTTPS_SCHEME.equals(url.getScheme());
            final int defaultPort = ssl ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT;
            final int port = url.getPort() != -1 ? url.getPort() : defaultPort;
            final var requestOptions = new RequestOptions()
                    .setMethod(HttpMethod.GET)
                    .setPort(port)
                    .setHost(url.getHost())
                    .setURI(url.getPath() + (url.getQuery() != null ? "?" + url.getQuery() : ""))
                    .setSsl(ssl);

            httpClient.request(requestOptions)
                    .onSuccess(req -> req.send()
                            .onSuccess(response -> {
                                if (response.statusCode() != HTTP_OK) {
                                    future.completeExceptionally(new CliException(
                                            "Failed to download export: HTTP " + response.statusCode(),
                                            APPLICATION_ERROR_RETURN_CODE));
                                    return;
                                }
                                response.body()
                                        .onSuccess(buffer -> future.complete(buffer.getBytes()))
                                        .onFailure(future::completeExceptionally);
                            })
                            .onFailure(future::completeExceptionally))
                    .onFailure(future::completeExceptionally);

            final var bytes = future.get(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Files.write(outputPath, bytes);
        } catch (IOException ex) {
            deleteQuietly(outputPath);
            throw new CliException("Failed to write export file: " + ex.getMessage(), ex,
                    APPLICATION_ERROR_RETURN_CODE);
        } catch (InterruptedException ex) {
            deleteQuietly(outputPath);
            Thread.currentThread().interrupt();
            throw new CliException("Download interrupted.", ex, APPLICATION_ERROR_RETURN_CODE);
        } catch (TimeoutException ex) {
            deleteQuietly(outputPath);
            throw new CliException("Export download timed out after " + DOWNLOAD_TIMEOUT_SECONDS
                    + " seconds.", ex, APPLICATION_ERROR_RETURN_CODE);
        } catch (Exception ex) {
            deleteQuietly(outputPath);
            final var cause = ex.getCause() instanceof CliException ce ? ce : null;
            if (cause != null) {
                throw cause;
            }
            throw new CliException("Failed to download export: " + ex.getMessage(), ex,
                    APPLICATION_ERROR_RETURN_CODE);
        }
    }

    private void deleteQuietly(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupEx) {
            log.debugf(cleanupEx, "Failed to delete partial export file: %s", path);
        }
    }
}
