package io.apicurio.registry.agents.a2a.signature;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serves JSON Web Key Sets for the signature verification tests, and counts the requests made for each
 * path, so tests can assert that a key set was fetched, or never was.
 * <p>
 * It listens on one loopback port for the whole test JVM, so a test profile can point the registry at it
 * before Quarkus starts. Each test class uses its own paths.
 * </p>
 */
public final class JwksStub {

    private static final JwksStub INSTANCE = new JwksStub();

    private final HttpServer server;
    private final Map<String, String> keySets = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

    private JwksStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "jwks-stub");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    public static JwksStub get() {
        return INSTANCE;
    }

    public static int port() {
        return INSTANCE.server.getAddress().getPort();
    }

    /** The URL of a path on the stub, with the host given (to test host allow-lists). */
    public static String url(String host, String path) {
        return "http://" + host + ":" + port() + path;
    }

    public static String url(String path) {
        return url("127.0.0.1", path);
    }

    /** Serves this key set at the path from now on. */
    public void serve(String path, String jwks) {
        keySets.put(path, jwks);
    }

    /** The number of requests received for the path since the last {@link #resetCount}. */
    public int requestCount(String path) {
        AtomicInteger count = requests.get(path);
        return count == null ? 0 : count.get();
    }

    public void resetCount(String path) {
        requests.remove(path);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
        String body = keySets.get(path);
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/jwk-set+json");
        exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length == 0 ? -1 : bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
