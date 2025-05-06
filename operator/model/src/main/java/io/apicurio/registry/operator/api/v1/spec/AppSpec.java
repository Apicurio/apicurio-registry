package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
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
     * DEPRECATED: Use the `app.storage.type` and `app.storage.sql` fields instead. The operator will attempt
     * to update the fields automatically.
     */
    @Deprecated(since = "3.0.7")
    @JsonProperty("sql")
    @JsonPropertyDescription("""
            DEPRECATED: Use the `app.storage.type` and `app.storage.sql` fields instead.
            The operator will attempt to update the fields automatically.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private DeprecatedSqlSpec sql;

    /**
     * DEPRECATED: Use the `app.storage.type` and `app.storage.sql` fields instead. The operator will attempt
     * to update the fields automatically.
     */
    @JsonProperty("kafkasql")
    @JsonPropertyDescription("""
            DEPRECATED: Use the `app.storage.type` and `app.storage.kafkasql` fields instead.
             The operator will attempt to update the fields automatically.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private DeprecatedKafkasqlSpec kafkasql;

    public StorageSpec withStorage() {
        if (storage == null) {
            storage = new StorageSpec();
        }
        return storage;
    }
}
