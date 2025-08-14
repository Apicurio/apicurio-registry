package io.apicurio.tests;

import com.microsoft.kiota.ApiException;
import io.apicurio.deployment.PortForwardManager;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.utils.tests.SimpleDisplayName;
import io.apicurio.registry.utils.tests.TestUtils;
import io.apicurio.tests.utils.Constants;
import io.apicurio.tests.utils.RegistryWaitUtils;
import io.apicurio.tests.utils.RestConstants;
import io.apicurio.tests.utils.TestSeparator;
import io.quarkus.test.common.http.TestHTTPResource;
import io.restassured.RestAssured;
import io.restassured.parsing.Parser;
import io.restassured.response.Response;
import io.vertx.core.Vertx;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Base class for all base classes for integration tests or for integration tests directly. This class must
 * not contain any functionality nor implement any beforeAll, beforeEach.
 */
@DisplayNameGeneration(SimpleDisplayName.class)
@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(PortForwardManager.class)
public class ApicurioRegistryBaseIT implements TestSeparator, Constants {

    private static final Logger log = LoggerFactory.getLogger(TestUtils.class);

    @TestHTTPResource
    static URL REGISTRY_URL;

    protected final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    protected Function<Exception, Integer> errorCodeExtractor = e -> ((ApiException) e)
            .getResponseStatusCode();

    protected Vertx vertx;
    protected RegistryClient registryClient;

    protected String authServerUrlConfigured;

    protected RegistryClient createRegistryClient(Vertx vertx) {
        return RegistryClientFactory.create(RegistryClientOptions.create(getRegistryV3ApiUrl(), vertx).retry());
    }

    @BeforeAll
    void prepareRestAssured() {
        vertx = Vertx.vertx();
        authServerUrlConfigured = Optional
                .ofNullable(ConfigProvider.getConfig().getConfigValue("quarkus.oidc.token-path").getValue())
                .orElse("http://localhost:8090/realms/registry/protocol/openid-connect/token");
        registryClient = createRegistryClient(vertx);
        RestAssured.baseURI = getRegistryV3ApiUrl();
        logger.info("RestAssured configured with {}", RestAssured.baseURI);
        RestAssured.defaultParser = Parser.JSON;
        RestAssured.urlEncodingEnabled = false;

        // Make sure all global rules are deleted, anything else should be isolated
        // within a group or artifact on a per-test basis.
        registryClient.admin().rules().delete();
    }

    @AfterAll
    void closeVertx() {
        vertx.close();
    }

    // Custom TestWatcher implementation
    static class RegistryApiErrorWatcher implements TestWatcher {

        public static Throwable getRootCause(Throwable throwable) {
            Throwable cause = throwable;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            return cause;
        }

        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            log.error("=== TEST FAILED ===");
            if (context.getTestClass().isPresent()) {
                log.error("Class: {}", context.getTestClass().get());
            }
            log.error("Test:  {}", context.getDisplayName());
            log.error("ID:    {}", context.getUniqueId());
            log.error("Error: {} - {}", cause.getClass().getSimpleName(), cause.getMessage());
            Throwable rootCause = getRootCause(cause);
            if (rootCause instanceof RuleViolationProblemDetails) {
                logProblemDetails((RuleViolationProblemDetails) rootCause);
            } else if (rootCause instanceof ProblemDetails) {
                logProblemDetails((ProblemDetails) rootCause);
            }
            // Optional: print stack trace or log somewhere else
            log.error("Root Cause", rootCause);
            log.error("=== =========== ===");
        }

        private void logProblemDetails(ProblemDetails cause) {
            log.error("Problem Details");
            log.error("    Name:     {}", cause.getName());
            log.error("    Title:    {}", cause.getTitle());
            log.error("    Detail:   {}", cause.getDetail());
            log.error("    Instance: {}", cause.getInstance());
            log.error("    Type:     {}", cause.getType());
            log.error("    Message:  {}", cause.getMessage());
            log.error("    Status:   {}", cause.getStatus());
        }

        private void logProblemDetails(RuleViolationProblemDetails cause) {
            log.error("Rule Violation Problem Details");
            log.error("    Name:     {}", cause.getName());
            log.error("    Title:    {}", cause.getTitle());
            log.error("    Detail:   {}", cause.getDetail());
            log.error("    Instance: {}", cause.getInstance());
            log.error("    Type:     {}", cause.getType());
            log.error("    Message:  {}", cause.getMessage());
            log.error("    Status:   {}", cause.getStatus());
            log.error("    Causes:");
            cause.getCauses().forEach((cause1) -> {
                log.error("        Context:     {}", cause1.getContext());
                log.error("        Description: {}", cause1.getDescription());
                log.error("        ---");
            });
        }

        @Override
        public void testSuccessful(ExtensionContext context) {
            System.out.println("Test passed: " + context.getDisplayName());
        }
    }

    @RegisterExtension
    RegistryApiErrorWatcher watcher = new RegistryApiErrorWatcher();

    private static String normalizeGroupId(String groupId) {
        return groupId != null ? groupId : "default"; // TODO
    }

    protected CreateArtifactResponse createArtifact(String groupId, String artifactId, String artifactType,
            String content, String contentType, IfArtifactExists ifExists,
            Consumer<CreateArtifact> customizer) throws Exception {
        CreateArtifact createArtifact = TestUtils.clientCreateArtifact(artifactId, artifactType, content,
                contentType);
        if (customizer != null) {
            customizer.accept(createArtifact);
        }
        var response = registryClient.groups().byGroupId(groupId).artifacts().post(createArtifact, config -> {
            config.queryParameters.canonical = false;
            if (ifExists != null) {
                config.queryParameters.ifExists = ifExists;
            }
        });

        // make sure we have schema registered
        ensureClusterSync(response.getVersion().getGlobalId());
        ensureClusterSync(normalizeGroupId(response.getArtifact().getGroupId()),
                response.getArtifact().getArtifactId(), String.valueOf(response.getVersion().getVersion()));

        // make sure content is available
        ensureClusterSyncContentId(response.getVersion().getContentId());

        // Wait for the artifact version to be available across all replicas.
        Thread.sleep(1000);

        return response;
    }

    protected VersionMetaData createArtifactVersion(String groupId, String artifactId, String content,
            String contentType, Consumer<CreateVersion> customizer) throws Exception {
        CreateVersion createVersion = TestUtils.clientCreateVersion(content, contentType);
        if (customizer != null) {
            customizer.accept(createVersion);
        }
        VersionMetaData meta = registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().post(createVersion);

        // wait for storage
        ensureClusterSync(meta.getGlobalId());
        ensureClusterSync(normalizeGroupId(meta.getGroupId()), meta.getArtifactId(),
                String.valueOf(meta.getVersion()));
        ensureClusterSyncContentId(meta.getContentId());

        // Wait for the artifact version to be available across all replicas.
        Thread.sleep(1000);

        return meta;
    }

    // DO NOT USE FOR CREATE OR UPDATE OPERATIONS
    protected void retryOp(RegistryWaitUtils.ConsumerExc<RegistryClient> registryOp) throws Exception {
        RegistryWaitUtils.retry(registryClient, registryOp);
    }

    // DO NOT USE FOR CREATE OR UPDATE OPERATIONS
    protected void retryAssertClientError(String expectedErrorName, int expectedCode,
            RegistryWaitUtils.ConsumerExc<RegistryClient> registryOp,
            Function<Exception, Integer> errorCodeExtractor) throws Exception {
        RegistryWaitUtils.retry(registryClient, (rc) -> {
            assertClientError(expectedErrorName, expectedCode, () -> registryOp.run(rc), errorCodeExtractor);
        });
    }

    private void ensureClusterSync(Long globalId) throws Exception {
        retry(() -> registryClient.ids().globalIds().byGlobalId(globalId));
    }

    private void ensureClusterSyncContentId(Long contentId) throws Exception {
        retry(() -> registryClient.ids().contentIds().byContentId(contentId));
    }

    private void ensureClusterSync(String groupId, String artifactId, String version) throws Exception {
        retry(() -> registryClient.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions()
                .byVersionExpression(version).get());
    }

    private void ensureClusterSync(Consumer<RegistryClient> function) throws Exception {
        retry(() -> function.accept(registryClient));
    }

    protected List<String> listArtifactVersions(RegistryClient rc, String groupId, String artifactId) {
        return rc.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get(config -> {
            config.queryParameters.limit = 10;
            config.queryParameters.offset = 0;
        }).getVersions().stream().map(SearchedVersion::getVersion).collect(Collectors.toList());
    }

    public static String resourceToString(String resourceName) {
        try (InputStream stream = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourceName)) {
            assertNotNull(stream, "Resource not found: " + resourceName);
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getRegistryApiUrl() {
        return getRegistryBaseUrl().concat("/apis");
    }

    public static String getRegistryV3ApiUrl() {
        return getRegistryApiUrl().concat("/registry/v3");
    }

    public static String getRegistryBaseUrl() {
        if (REGISTRY_URL != null) {
            String baseUrl = REGISTRY_URL.toString();
            if (System.getProperty("quarkus.http.test-port", "").endsWith("443")) {
                baseUrl = baseUrl.replace("http://", "https://");
            }
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            System.out.println("**** Registry Base URL: " + baseUrl);
            return baseUrl;
        } else {
            return String.format("%s://%s:%s",
                    System.getProperty("quarkus.http.test-scheme", "http"),
                    System.getProperty("quarkus.http.test-host", "localhost"),
                    System.getProperty("quarkus.http.test-port", "8080"));
        }
    }

    public String generateArtifactId() {
        return UUID.randomUUID().toString();
    }

    @FunctionalInterface
    public interface RunnableExc {
        void run() throws Exception;

    }

    public void retry(TestUtils.RunnableExc runnable) throws Exception {
        retry(() -> {
            runnable.run();
            return null;
        });
    }

    public void retry(TestUtils.RunnableExc runnable, long delta) throws Exception {
        retry(() -> {
            runnable.run();
            return null;
        }, delta);
    }

    public <T> T retry(Callable<T> callable) throws Exception {
        return retry(callable, "Action #" + System.currentTimeMillis(), 20);
    }

    public <T> T retry(Callable<T> callable, long delta) throws Exception {
        return retry(callable, "Action #" + System.currentTimeMillis(), 20, delta);
    }

    public void retry(TestUtils.RunnableExc runnable, String name, int maxRetries) throws Exception {
        retry(() -> {
            runnable.run();
            return null;
        }, name, maxRetries);
    }

    private <T> T retry(Callable<T> callable, String name, int maxRetries) throws Exception {
        return retry(callable, name, maxRetries, 100L);
    }

    private <T> T retry(Callable<T> callable, String name, int maxRetries, long delta) throws Exception {
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
                Thread.sleep(delta * attempt);
                tries--;
                attempt++;
            }
        }
        log.debug("Action [{}] failed after {} attempts.", name, attempt);
        Assertions.assertTrue(tries > 0, String.format("Failed handle callable: %s [%s]", callable, error));
        throw new IllegalStateException("Should not be here!");
    }

    public void assertClientError(String expectedErrorName, int expectedCode, TestUtils.RunnableExc runnable,
            Function<Exception, Integer> errorCodeExtractor) throws Exception {
        try {
            internalAssertClientError(expectedErrorName, expectedCode, runnable, errorCodeExtractor);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void assertClientError(String expectedErrorName, int expectedCode, TestUtils.RunnableExc runnable,
            boolean retry, Function<Exception, Integer> errorCodeExtractor) throws Exception {
        if (retry) {
            retry(() -> internalAssertClientError(expectedErrorName, expectedCode, runnable,
                    errorCodeExtractor));
        } else {
            internalAssertClientError(expectedErrorName, expectedCode, runnable, errorCodeExtractor);
        }
    }

    private void internalAssertClientError(String expectedErrorName, int expectedCode,
            TestUtils.RunnableExc runnable, Function<Exception, Integer> errorCodeExtractor) {
        try {
            runnable.run();
            Assertions.fail("Expected (but didn't get) a registry client application exception with code: "
                    + expectedCode);
        } catch (Exception e) {
            if (e instanceof io.apicurio.registry.rest.client.models.RuleViolationProblemDetails) {
                Assertions.assertEquals(expectedErrorName,
                        ((io.apicurio.registry.rest.client.models.RuleViolationProblemDetails) e).getName());
                Assertions.assertEquals(expectedCode,
                        ((io.apicurio.registry.rest.client.models.RuleViolationProblemDetails) e)
                                .getStatus());
            } else if (e instanceof io.apicurio.registry.rest.client.models.ProblemDetails) {
                Assertions.assertEquals(expectedErrorName,
                        ((io.apicurio.registry.rest.client.models.ProblemDetails) e).getName());
                Assertions.assertEquals(expectedCode,
                        ((io.apicurio.registry.rest.client.models.ProblemDetails) e).getStatus());
            } else {
                throw new RuntimeException("Unhandled exception type");
            }
        }
    }

    // some impl details ...

    public void waitForSchema(Predicate<Long> schemaFinder, byte[] bytes) throws Exception {
        waitForSchema(schemaFinder, bytes, ByteBuffer::getLong);
    }

    public void waitForSchema(Predicate<Long> schemaFinder, byte[] bytes,
            Function<ByteBuffer, Long> globalIdExtractor) throws Exception {
        waitForSchemaCustom(schemaFinder, bytes, input -> {
            ByteBuffer buffer = ByteBuffer.wrap(input);
            buffer.get(); // magic byte
            return globalIdExtractor.apply(buffer);
        });
    }

    // we can have non-default Apicurio serialization; e.g. ExtJsonConverter
    public void waitForSchemaCustom(Predicate<Long> schemaFinder, byte[] bytes,
            Function<byte[], Long> globalIdExtractor) throws Exception {
        long id = globalIdExtractor.apply(bytes);
        boolean schemaExists = retry(() -> schemaFinder.test(id));
        Assertions.assertTrue(schemaExists); // wait for global id to populate
    }

    public final String normalizeMultiLineString(String value) throws Exception {
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

    public Response getArtifact(String groupId, String artifactId) {
        return getArtifact(groupId, artifactId, "", 200);
    }

    public Response getArtifact(String groupId, String artifactId, int returnCode) {
        return getArtifact(groupId, artifactId, "", returnCode);
    }

    public Response getArtifact(String groupId, String artifactId, String version, int returnCode) {
        return getRequest(RestConstants.JSON, "/groups/" + encodeURIComponent(groupId) + "/artifacts/"
                + encodeURIComponent(artifactId) + "/" + version, returnCode);
    }

    private String encodeURIComponent(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Response getRequest(String contentType, String endpoint, int returnCode) {
        return given().when().contentType(contentType).get(getRegistryV3ApiUrl() + endpoint).then()
                .statusCode(returnCode).extract().response();
    }

    public Response getRequest(String contentType, URL endpoint, int returnCode) {
        return given().when().contentType(contentType).get(getRegistryV3ApiUrl() + endpoint).then()
                .statusCode(returnCode).extract().response();
    }

    public Response postRequest(String contentType, String body, String endpoint, int returnCode) {
        return given().when().contentType(contentType).body(body).post(getRegistryV3ApiUrl() + endpoint)
                .then().statusCode(returnCode).extract().response();
    }

    public Response postRequest(String contentType, String body, URL endpoint, int returnCode) {
        return given().when().contentType(contentType).body(body).post(getRegistryV3ApiUrl() + endpoint)
                .then().statusCode(returnCode).extract().response();
    }

    public Response putRequest(String contentType, String body, String endpoint, int returnCode) {
        return given().when().contentType(contentType).body(body).put(getRegistryV3ApiUrl() + endpoint).then()
                .statusCode(returnCode).extract().response();
    }

    public Response putRequest(String contentType, String body, URL endpoint, int returnCode) {
        return given().when().contentType(contentType).body(body).put(getRegistryV3ApiUrl() + endpoint).then()
                .statusCode(returnCode).extract().response();
    }

    public Response deleteRequest(String contentType, String endpoint, int returnCode) {
        return given().when().contentType(contentType).delete(getRegistryV3ApiUrl() + endpoint).then()
                .statusCode(returnCode).extract().response();
    }

    public Response rulesPostRequest(String contentType, String rule, String endpoint, int returnCode) {
        return given().when().contentType(contentType).body(rule).post(getRegistryV3ApiUrl() + endpoint)
                .then().statusCode(returnCode).extract().response();
    }

    public Response rulesPostRequest(String contentType, String rule, URL endpoint, int returnCode) {
        return given().when().contentType(contentType).body(rule).post(getRegistryV3ApiUrl() + endpoint)
                .then().statusCode(returnCode).extract().response();
    }

    public Response rulesGetRequest(String contentType, String endpoint, int returnCode) {
        return given().when().contentType(contentType).get(getRegistryV3ApiUrl() + endpoint).then()
                .statusCode(returnCode).extract().response();
    }

    public Response rulesPutRequest(String contentType, String rule, String endpoint, int returnCode) {
        return given().when().contentType(contentType).body(rule).put(getRegistryV3ApiUrl() + endpoint).then()
                .statusCode(returnCode).extract().response();
    }

    public Response rulesDeleteRequest(String contentType, String endpoint, int returnCode) {
        return given().when().contentType(contentType).delete(getRegistryV3ApiUrl() + endpoint).then()
                .statusCode(returnCode).extract().response();
    }

    public Response artifactPostRequest(String artifactId, String contentType, String body, String endpoint,
            int returnCode) {
        return given().when().header("X-Registry-Artifactid", artifactId).contentType(contentType).body(body)
                .post(getRegistryV3ApiUrl() + endpoint).then().statusCode(returnCode).extract().response();
    }

    protected void assertNotAuthorized(Exception exception) {
        assertNotNull(exception);
        Assertions.assertEquals(RuntimeException.class, exception.getClass());
        Assertions.assertTrue(exception.getMessage()
                .contains("unauthorized_client: Invalid client or Invalid client credentials"));
    }

    protected void assertForbidden(Exception exception) {
        Assertions.assertEquals(ApiException.class, exception.getClass());
        Assertions.assertEquals(403, ((ApiException) exception).getResponseStatusCode());
    }
}
