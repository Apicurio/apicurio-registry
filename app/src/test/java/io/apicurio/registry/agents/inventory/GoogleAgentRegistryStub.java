package io.apicurio.registry.agents.inventory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stand-in for the Google Cloud Agent Registry API v1 list method: it serves the pages set for a
 * location, records the Authorization header of each request per location, and answers with the
 * status set for a location instead, when there is one. A location with nothing set gets 404, as
 * the real API answers for an unknown location.
 * <p>
 * It listens on one loopback port for the whole test JVM, so the test profile can point the
 * registry at it before Quarkus starts. Each test class uses its own locations and resets them.
 */
public final class GoogleAgentRegistryStub {

    private static final Pattern LIST_PATH = Pattern.compile("/v1/(.+)/agents");
    private static final Pattern PAGE_TOKEN = Pattern.compile("pageToken=page-(\\d+)");
    private static final GoogleAgentRegistryStub INSTANCE = new GoogleAgentRegistryStub();

    private final HttpServer server;
    private final Map<String, List<String>> authorizations = new ConcurrentHashMap<>();
    private final Map<String, List<String>> pages = new ConcurrentHashMap<>();
    private final Map<String, Integer> statuses = new ConcurrentHashMap<>();

    private GoogleAgentRegistryStub() {
        try {
            // Port 0: the system picks a free port and the server holds it from the start.
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/v1/", this::handle);
        server.setExecutor(null);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
    }

    static GoogleAgentRegistryStub get() {
        return INSTANCE;
    }

    static String url() {
        return "http://127.0.0.1:" + INSTANCE.server.getAddress().getPort();
    }

    /** Forgets everything set and recorded for the location. */
    void reset(String location) {
        authorizations.remove(location);
        pages.remove(location);
        statuses.remove(location);
    }

    /** Serves the given list responses for the location, one per page, in order. */
    void pages(String location, String... responses) {
        statuses.remove(location);
        pages.put(location, List.of(responses));
    }

    /** Answers every list request for the location with this status. */
    void status(String location, int status) {
        statuses.put(location, status);
    }

    /** The Authorization header of each request for the location, "null" when there was none. */
    List<String> authorizations(String location) {
        return authorizations.getOrDefault(location, List.of());
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            Matcher path = LIST_PATH.matcher(exchange.getRequestURI().getPath());
            if (!path.matches()) {
                respond(exchange, 400, "{\"error\":{\"code\":400}}");
                return;
            }
            String location = path.group(1);
            authorizations.computeIfAbsent(location, l -> new CopyOnWriteArrayList<>())
                    .add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            String query = exchange.getRequestURI().getQuery();
            Matcher token = PAGE_TOKEN.matcher(query == null ? "" : query);
            int page = token.find() ? Integer.parseInt(token.group(1)) : 0;
            Integer status = statuses.get(location);
            List<String> responses = pages.get(location);
            if (status != null || responses == null || page >= responses.size()) {
                int code = status != null ? status : 404;
                respond(exchange, code, "{\"error\":{\"code\":" + code + "}}");
                return;
            }
            respond(exchange, 200, responses.get(page));
        } catch (RuntimeException e) {
            // Answer instead of leaving the client to time out, so a broken test fails clearly.
            respond(exchange, 500, "{\"error\":{\"code\":500}}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
