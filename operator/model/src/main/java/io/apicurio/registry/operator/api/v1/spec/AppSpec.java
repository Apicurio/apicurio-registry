package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.spec.auth.AuthSpec;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
// Keep properties and fields alphabetical
@JsonPropertyOrder(alphabetic = true)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppSpec extends ComponentSpec {

    /**
     * Configure storage for Apicurio Registry backend (app).
     */
    @JsonProperty("storage")
    @JsonPropertyDescription("""
            Configure storage for Apicurio Registry backend (app).
            """)
    @JsonSetter(nulls = SKIP)
    private StorageSpec storage;

    /**
     * Configure features of the Apicurio Registry application.
     */
    @JsonProperty("features")
    @JsonPropertyDescription("""
            Configure features of the Apicurio Registry backend (app).
            """)
    @JsonSetter(nulls = SKIP)
    private AppFeaturesSpec features;

    /**
     * Configure features of the Apicurio Registry application.
     */
    @JsonProperty("auth")
    @JsonPropertyDescription("""
            Configure authentication and authorization of Apicurio Registry.
            """)
    @JsonSetter(nulls = SKIP)
    private AuthSpec auth;

    /**
     * Configure features of the Apicurio Registry application.
     */
    @JsonProperty("tls")
    @JsonPropertyDescription("""
            Configure tls of Apicurio Registry.
            """)
    @JsonSetter(nulls = SKIP)
    private TLSSpec tls;

    /**
     * Configure OpenTelemetry observability for Apicurio Registry.
     */
    @JsonProperty("otel")
    @JsonPropertyDescription("""
            Configure OpenTelemetry observability for Apicurio Registry.
            When enabled, Registry exports distributed traces and metrics to an OpenTelemetry collector.
            """)
    @JsonSetter(nulls = SKIP)
    private OTelSpec otel;

    /**
     * Configure Elasticsearch-based search indexing for Apicurio Registry.
     */
    @JsonProperty("searchIndex")
    @JsonPropertyDescription("""
            Configure Elasticsearch-based search indexing for Apicurio Registry.
            When enabled, Registry indexes artifact content in Elasticsearch to support
            full-text content and structure searches.""")
    @JsonSetter(nulls = SKIP)
    private SearchIndexSpec searchIndex;

    public StorageSpec withStorage() {
        if (storage == null) {
            storage = new StorageSpec();
        }
        return storage;
    }
}
