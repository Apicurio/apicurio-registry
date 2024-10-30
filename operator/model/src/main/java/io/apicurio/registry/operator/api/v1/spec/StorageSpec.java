package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "type", "sql", "kafkasql" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class StorageSpec {

    /**
     * Configure the type of storage that Apicurio Registry backend (app) will use:
     * <ul>
     * <li><code>&lt;empty&gt;</code> - in-memory storage.</li>
     * <li><code>postgresql</code> - Postgresql storage type, must be further configured using the
     * <code>app.storage.sql</code> field.</li>
     * <li><code>kafkasql</code> - KafkaSQL storage type, must be further configured using the
     * <code>app.storage.kafkasql</code> field.</li>
     * </ul>
     * <b>IMPORTANT:</b> Defaults to the in-memory storage, which is not suitable for production.
     *
     * @see io.apicurio.registry.operator.api.v1.spec.StorageType
     */
    @JsonProperty("type")
    @JsonPropertyDescription("""
            Configure type of storage that Apicurio Registry backend (app) will use:

              * <empty> - in-memory storage.
              * `postgresql` - Postgresql storage type, must be further configured using the `app.storage.sql` field.
              * `kafkasql` - KafkaSQL storage type, must be further configured using the `app.storage.kafkasql` field.

            IMPORTANT: Defaults to the in-memory storage, which is not suitable for production.""")
    @JsonSetter(nulls = SKIP)
    private StorageType type;

    /**
     * Configure SQL storage types.
     */
    @JsonProperty("sql")
    @JsonPropertyDescription("""
            Configure SQL storage types.""")
    @JsonSetter(nulls = SKIP)
    private SqlSpec sql;

    /**
     * Configure KafkaSQL storage type.
     */
    @JsonProperty("kafkasql")
    @JsonPropertyDescription("""
            Configure KafkaSQL storage type.""")
    @JsonSetter(nulls = SKIP)
    private KafkaSqlSpec kafkasql;

    public SqlSpec withSql() {
        if (sql == null) {
            sql = new SqlSpec();
        }
        return sql;
    }

    public KafkaSqlSpec withKafkasql() {
        if (kafkasql == null) {
            kafkasql = new KafkaSqlSpec();
        }
        return kafkasql;
    }
}
