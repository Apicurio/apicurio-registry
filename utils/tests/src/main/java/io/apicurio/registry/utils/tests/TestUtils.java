/*
 * Copyright 2020 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.utils.tests;


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.apicurio.registry.utils.IoUtil;

/**
 * @author Ales Justin
 * @author Jakub Stejskal
 */
public class TestUtils {
    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    private static final String DEFAULT_REGISTRY_HOST = "localhost";
    private static final int DEFAULT_REGISTRY_PORT = 8081;

    private static final String REGISTRY_HOST = System.getenv().getOrDefault("REGISTRY_HOST", DEFAULT_REGISTRY_HOST);
    private static final int REGISTRY_PORT = Integer.parseInt(System.getenv().getOrDefault("REGISTRY_PORT", String.valueOf(DEFAULT_REGISTRY_PORT)));
    private static final String EXTERNAL_REGISTRY = System.getenv().getOrDefault("EXTERNAL_REGISTRY", "false");

    private TestUtils() {
        // All static methods
    }

    public static boolean isExternalRegistry() {
        return Boolean.parseBoolean(EXTERNAL_REGISTRY);
    }

    public static String getRegistryHost() {
        return REGISTRY_HOST;
    }

    public static int getRegistryPort() {
        return REGISTRY_PORT;
    }

    public static String getRegistryUIUrl() {
        return getRegistryBaseUrl().concat("/ui");
    }

    public static String getRegistryApiUrl() {
        return getRegistryBaseUrl().concat("/apis");
    }

    public static String getRegistryApiUrl(int port) {
        return getRegistryBaseUrl(port).concat("/apis");
    }

    public static String getRegistryV1ApiUrl() {
        return getRegistryApiUrl().concat("/registry/v1");
    }

    public static String getRegistryV2ApiUrl() {
        return getRegistryApiUrl().concat("/registry/v2");
    }

    public static String getRegistryV2ApiUrl(int testPort) {
        return getRegistryApiUrl(testPort).concat("/registry/v2");
    }

    public static String getRegistryBaseUrl() {
        if (isExternalRegistry()) {
            return String.format("http://%s:%s", REGISTRY_HOST, REGISTRY_PORT);
        } else {
            return String.format("http://%s:%s", DEFAULT_REGISTRY_HOST, DEFAULT_REGISTRY_PORT);
        }
    }

    public static String getRegistryBaseUrl(int port) {
        if (isExternalRegistry()) {
            return String.format("http://%s:%s", REGISTRY_HOST, port);
        } else {
            return String.format("http://%s:%s", DEFAULT_REGISTRY_HOST, port);
        }
    }

    /**
     * Method which try connection to registries. It's used as a initial check for registries availability.
     *
     * @return true if registries are ready for use, false in other cases
     */
    public static boolean isReachable() {
        try (Socket socket = new Socket()) {
            String host = isExternalRegistry() ? REGISTRY_HOST : DEFAULT_REGISTRY_HOST;
            int port = isExternalRegistry() ? REGISTRY_PORT : DEFAULT_REGISTRY_PORT;
            log.info("Trying to connect to {}:{}", host, port);
            socket.connect(new InetSocketAddress(host, port), 5_000);
            log.info("Client is able to connect to Registry instance");
            return  true;
        } catch (IOException ex) {
            log.warn("Cannot connect to Registry instance: {}", ex.getMessage());
            return false; // Either timeout or unreachable or failed DNS lookup.
        }
    }

    /**
     * Generic check if an endpoint is network reachable
     * @param host
     * @param port
     * @param component
     * @return true if it's possible to open a network connection to the endpoint
     */
    public static boolean isReachable(String host, int port, String component) {
        try (Socket socket = new Socket()) {
            log.info("Trying to connect to {}:{}", host, port);
            socket.connect(new InetSocketAddress(host, port), 5_000);
            log.info("Client is able to connect to " + component);
            return  true;
        } catch (IOException ex) {
            log.warn("Cannot connect to {}: {}", component, ex.getMessage());
            return false; // Either timeout or unreachable or failed DNS lookup.
        }
    }


    /**
     * Checks the readniess endpoint of the registry
     *
     * @return true if registry readiness endpoint replies sucessfully
     */
    public static boolean isReady(boolean logResponse) {
        return isReady(getRegistryBaseUrl(), "/health/ready", logResponse, "Apicurio Registry");
    }

    /**
     * Generic check of the /health/ready endpoint
     *
     * @param baseUrl
     * @param logResponse
     * @param component
     * @return true if the readiness endpoint replies successfully
     */
    public static boolean isReady(String baseUrl, String healthUrl, boolean logResponse, String component) {
        try {
            CloseableHttpResponse res = HttpClients.createMinimal().execute(new HttpGet(baseUrl.concat(healthUrl)));
            boolean ok = res.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
            if (ok) {
                log.info(component + " is ready");
            }
            if (logResponse) {
                log.info(IoUtil.toString(res.getEntity().getContent()));
            }
            return ok;
        } catch (IOException e) {
            log.warn(component + " is not ready {}", e.getMessage());
            return false;
        }
    }

    // ---

    /**
     * Poll the given {@code ready} function every {@code pollIntervalMs} milliseconds until it returns true,
     * or throw a TimeoutException if it doesn't returns true within {@code timeoutMs} milliseconds.
     * (helpful if you have several calls which need to share a common timeout)
     *
     * @return The remaining time left until timeout occurs
     */
    public static long waitFor(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready) throws TimeoutException {
        return waitFor(description, pollIntervalMs, timeoutMs, ready, () -> {});
    }

    public static long waitFor(String description, long pollIntervalMs, long timeoutMs, BooleanSupplier ready, Runnable onTimeout) throws TimeoutException {
        log.debug("Waiting for {}", description);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            boolean result;
            try {
                result = ready.getAsBoolean();
            } catch (Throwable e) {
                result = false;
            }
            long timeLeft = deadline - System.currentTimeMillis();
            if (result) {
                return timeLeft;
            }
            if (timeLeft <= 0) {
                onTimeout.run();
                TimeoutException exception = new TimeoutException("Timeout after " + timeoutMs + " ms waiting for " + description);
                exception.printStackTrace();
                throw exception;
            }
            long sleepTime = Math.min(pollIntervalMs, timeLeft);
            if (log.isTraceEnabled()) {
                log.trace("{} not ready, will try again in {} ms ({}ms till timeout)", description, sleepTime, timeLeft);
            }
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                return deadline - System.currentTimeMillis();
            }
        }
    }

    /**
     * Method to create and write String content file.
     *
     * @param filePath path to file
     * @param text     content
     */
    public static void writeFile(String filePath, String text) {
        try {
            Files.write(new File(filePath).toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.info("Exception during writing text in file");
        }
    }

    public static void writeFile(Path filePath, String text) {
        try {
            Files.write(filePath, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.info("Exception during writing text in file");
        }
    }

    public static String generateTopic() {
        return generateTopic("topic-");
    }

    public static String generateTopic(String prefix) {
        return prefix + UUID.randomUUID().toString();
    }

    public static String generateSubject() {
        return "s" + generateArtifactId().replace("-", "x");
    }

    public static String generateArtifactId() {
        return UUID.randomUUID().toString();
    }

    public static String generateGroupId() {
        return UUID.randomUUID().toString();
    }

    @FunctionalInterface
    public interface RunnableExc {
        void run() throws Exception;
    }

    public static void retry(RunnableExc runnable) throws Exception {
        retry(() -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T retry(Callable<T> callable) throws Exception {
        return retry(callable, "Action #" + System.currentTimeMillis(), 20);
    }

    public static void retry(RunnableExc runnable, String name, int maxRetries) throws Exception {
        retry(() -> {
            runnable.run();
            return null;
        }, name, maxRetries);
    }

    private static <T> T retry(Callable<T> callable, String name, int maxRetries) throws Exception {
        Throwable error = null;
        int tries = maxRetries;
        int attempt = 1;
        while (tries > 0) {
            try {
                if (attempt > 1) {
                    log.debug("Retrying action [{}].  Attempt #{}", name, attempt);
                }
                return callable.call();
            } catch (Throwable t) {
                if (error == null) {
                    error = t;
                } else {
                    error.addSuppressed(t);
                }
                Thread.sleep(100L * attempt);
                tries--;
                attempt++;
            }
        }
        log.debug("Action [{}] failed after {} attempts.", name, attempt);
        Assertions.assertTrue(tries > 0, String.format("Failed handle callable: %s [%s]", callable, error));
        throw new IllegalStateException("Should not be here!");
    }

    public static void assertClientError(String expectedErrorName, int expectedCode, RunnableExc runnable, Function<Exception, Integer> errorCodeExtractor) throws Exception {
        try {
            internalAssertClientError(expectedErrorName, expectedCode, runnable, errorCodeExtractor);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void assertClientError(String expectedErrorName, int expectedCode, RunnableExc runnable, boolean retry, Function<Exception, Integer> errorCodeExtractor) throws Exception {
        if (retry) {
            retry(() -> internalAssertClientError(expectedErrorName, expectedCode, runnable, errorCodeExtractor));
        } else {
            internalAssertClientError(expectedErrorName, expectedCode, runnable, errorCodeExtractor);
        }
    }

    private static void internalAssertClientError(String expectedErrorName, int expectedCode, RunnableExc runnable, Function<Exception, Integer> errorCodeExtractor) {
        try {
            runnable.run();
            Assertions.fail("Expected (but didn't get) a registry client application exception with code: " + expectedCode);
        } catch (Exception e) {
            Assertions.assertEquals(expectedErrorName, e.getClass().getSimpleName(), () -> "e: " + e);
            Assertions.assertEquals(expectedCode, errorCodeExtractor.apply(e));
        }
    }

    // some impl details ...

    public static void waitForSchema(Predicate<Long> schemaFinder, byte[] bytes) throws Exception {
        waitForSchema(schemaFinder, bytes, ByteBuffer::getLong);
    }

    public static void waitForSchema(Predicate<Long> schemaFinder, byte[] bytes, Function<ByteBuffer, Long> globalIdExtractor) throws Exception {
        waitForSchemaCustom(schemaFinder, bytes, input -> {
            ByteBuffer buffer = ByteBuffer.wrap(input);
            buffer.get(); // magic byte
            return globalIdExtractor.apply(buffer);
        });
    }

    // we can have non-default Apicurio serialization; e.g. ExtJsonConverter
    public static void waitForSchemaCustom(Predicate<Long> schemaFinder, byte[] bytes, Function<byte[], Long> globalIdExtractor) throws Exception {
        long id = globalIdExtractor.apply(bytes);
        boolean schemaExists = retry(() -> schemaFinder.test(id));
        Assertions.assertTrue(schemaExists); // wait for global id to populate
    }

    public static final String normalizeMultiLineString(String value) throws Exception {
        StringBuilder builder = new StringBuilder();
        BufferedReader reader = new BufferedReader(new StringReader(value));
        String line = reader.readLine();
        while (line != null) {
            builder.append(line);
            builder.append("\n");
            line = reader.readLine();
        }
        return builder.toString();
    }

}
