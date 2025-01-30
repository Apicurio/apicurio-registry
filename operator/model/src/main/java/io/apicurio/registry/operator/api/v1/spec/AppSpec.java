package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.spec.auth.AppAuthSpec;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "env", "ingress", "podTemplateSpec", "storage", "sql", "kafkasql", "features", "auth" })
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
    private AppAuthSpec auth;

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
