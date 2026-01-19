package io.apicurio.registry.metrics;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractRestMetricsTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractRestMetricsTest.class);

    @ConfigProperty(name = "quarkus.http.test-port")
    public int testPort;

    protected String baseURI;

    @BeforeEach
    public void beforeEach() {
        baseURI = "http://localhost:" + testPort; // Can't be static :(
    }

    private String metrics;

    protected String metrics(boolean refresh) {
        if (refresh || metrics == null) {
            metrics = given()
                    .when().get("/metrics")
                    .then()
                    .log().all().
                    extract().body().asString();

            log.warn(metrics);
        }
        return metrics;
    }

    protected void lineDoesMatch(String regex) {
        assertThat(metrics(false).lines().filter(line -> line.matches(regex)).findFirst()).isPresent();
    }

    protected void lineDoesNotMatch(String regex) {
        assertThat(metrics(false).lines().filter(line -> line.matches(regex)).findFirst()).isEmpty();
    }
}
