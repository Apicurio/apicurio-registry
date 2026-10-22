package io.apicurio.registry.operator.api.v1.spec.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.Duration;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static lombok.AccessLevel.PRIVATE;

@JsonDeserialize(using = JsonDeserializer.None.class)
@JsonInclude(NON_NULL)
@JsonPropertyOrder({"enabled", "cacheExpiration"})
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class BasicAuthSpec {

    @JsonProperty("enabled")
    @JsonPropertyDescription("""
            Enabled client credentials.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean enabled;

    @JsonProperty("cacheExpiration")
    @JsonPropertyDescription("""
            Client credentials token expiration time. This value is a string representing a duration:
                        
            | Abbreviation          | Time Unit    |
            |-----------------------|--------------|
            | ns, nano, nanos       | Nanosecond   |
            | us, µs, micro, micros | Microseconds |
            | ms, milli, millis     | Millisecond  |
            | s, sec, secs          | Second       |
            | m, min, mins          | Minute       |
            | h, hr, hour, hours    | Hour         |
            | d, day, days          | Day          |
            | w, wk, week, weeks    | Week         |
               
            Example: "1min1s"
            """)
    @JsonSetter(nulls = Nulls.SKIP)
    private Duration cacheExpiration;
}
