package io.apicurio.registry.client.common;

import com.microsoft.kiota.HttpMethod;
import com.microsoft.kiota.RequestAdapter;
import com.microsoft.kiota.RequestInformation;
import io.kiota.http.jdk.JDKRequestAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the JDK adapter honours {@link RegistryClientOptions#requestTimeout(long, long)}.
 */
class JdkAdapterTimeoutTest {

    private ServerSocket server;
    private final List<Socket> accepted = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startSilentServer() throws Exception {
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(() -> {
            try {
                while (!server.isClosed()) {
                    accepted.add(server.accept());
                }
            } catch (Exception e) {
                // server closed
            }
        }, "silent-server");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    @AfterEach
    void stopSilentServer() throws Exception {
        server.close();
        for (Socket socket : accepted) {
            socket.close();
        }
    }

    @Test
    void testConnectTimeoutDefaultsTo30Seconds() throws Exception {
        RequestAdapter adapter = createAdapter(RegistryClientOptions.create()
                .registryUrl(serverUrl())
                .httpAdapter(HttpAdapterType.JDK));

        assertEquals(Optional.of(Duration.ofSeconds(30)), httpClient(adapter).connectTimeout());
        assertEquals(Optional.empty(), nativeRequest(adapter).timeout());
    }

    @Test
    void testRequestTimeoutAppliesToConnectAndRequest() throws Exception {
        RequestAdapter adapter = createAdapter(RegistryClientOptions.create()
                .registryUrl(serverUrl())
                .httpAdapter(HttpAdapterType.JDK)
                .requestTimeout(1234, 567));

        assertEquals(Optional.of(Duration.ofMillis(1234)), httpClient(adapter).connectTimeout());
        assertEquals(Optional.of(Duration.ofMillis(567)), nativeRequest(adapter).timeout());
    }

    @Test
    void testRequestTimeoutAppliesWithBasicAuth() throws Exception {
        RequestAdapter adapter = createAdapter(RegistryClientOptions.create()
                .registryUrl(serverUrl())
                .httpAdapter(HttpAdapterType.JDK)
                .basicAuth("user", "pass")
                .requestTimeout(1000, 750));

        HttpRequest request = nativeRequest(adapter);
        assertEquals(Optional.of(Duration.ofMillis(750)), request.timeout());
        assertEquals(Optional.of("Basic dXNlcjpwYXNz"), request.headers().firstValue("Authorization"));
    }

    @Test
    void testUnresponsiveServerTimesOutWithinReadTimeout() {
        RequestAdapter adapter = createAdapter(RegistryClientOptions.create()
                .registryUrl(serverUrl())
                .httpAdapter(HttpAdapterType.JDK)
                .requestTimeout(5000, 500));

        long start = System.nanoTime();
        Exception ex = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> assertThrows(Exception.class,
                () -> adapter.sendPrimitive(requestInfo(), null, String.class)));
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertTrue(hasCause(ex, HttpTimeoutException.class),
                "Expected an HttpTimeoutException in the cause chain but got: " + ex);
        assertTrue(elapsedMs >= 500,
                "Expected timeout after ~500 ms but took " + elapsedMs + " ms");
    }

    private String serverUrl() {
        return "http://127.0.0.1:" + server.getLocalPort();
    }

    private RequestAdapter createAdapter(RegistryClientOptions options) {
        RequestAdapter adapter = RegistryClientRequestAdapterFactory.createRequestAdapter(options, Version.V3);
        adapter.setBaseUrl(serverUrl());
        return adapter;
    }

    private RequestInformation requestInfo() {
        RequestInformation info = new RequestInformation();
        info.httpMethod = HttpMethod.GET;
        info.setUri(URI.create(serverUrl() + "/apis/registry/v3/system/info"));
        return info;
    }

    private HttpRequest nativeRequest(RequestAdapter adapter) {
        return ((JDKRequestAdapter) adapter).convertToNativeRequest(requestInfo());
    }

    private static HttpClient httpClient(RequestAdapter adapter) throws Exception {
        Field field = JDKRequestAdapter.class.getDeclaredField("client");
        field.setAccessible(true);
        return (HttpClient) field.get(adapter);
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
