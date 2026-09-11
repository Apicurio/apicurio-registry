package io.apicurio.registry.operator.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrometheusTextParserTest {

    @Test
    public void testParsesSamples() {
        var samples = PrometheusTextParser.parse("""
                # HELP agroal_active_count Number of active connections
                # TYPE agroal_active_count gauge

                agroal_active_count 3.0
                rest_requests_seconds_count{method="GET",status_code_group="5xx"} 12.0
                some_metric 1.5 1699999999000
                """);

        assertThat(samples).hasSize(3);
        assertThat(samples.get(0).name()).isEqualTo("agroal_active_count");
        assertThat(samples.get(0).labels()).isEmpty();
        assertThat(samples.get(0).value()).isEqualTo(3.0);
        assertThat(samples.get(1).label("status_code_group")).isEqualTo("5xx");
        // A trailing timestamp is not part of the value.
        assertThat(samples.get(2).value()).isEqualTo(1.5);
    }

    /**
     * Registry tags REST metrics with the unsubstituted path, which contains braces, and a label value may
     * also contain commas and escaped quotes.
     */
    @Test
    public void testParsesLabelValueContainingSeparators() {
        var samples = PrometheusTextParser.parse(
                "rest_requests_seconds_count{path=\"/apis/registry/v3/groups/{groupId}\",note=\"a,b \\\"c\\\"\"} 7\n");

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).label("path")).isEqualTo("/apis/registry/v3/groups/{groupId}");
        assertThat(samples.get(0).label("note")).isEqualTo("a,b \"c\"");
    }

    /**
     * One unexpected line should not cost us every other metric on the endpoint.
     */
    @Test
    public void testSkipsWhatItCannotRead() {
        var samples = PrometheusTextParser.parse("""
                no_value_at_all
                broken{unclosed="label" 1
                not_a_number abc
                a_nan NaN
                good_metric 42
                """);

        assertThat(samples).hasSize(2);
        assertThat(samples.get(0).value()).isNaN();
        assertThat(samples.get(1).name()).isEqualTo("good_metric");
        assertThat(PrometheusTextParser.parse(null)).isEmpty();
    }
}
