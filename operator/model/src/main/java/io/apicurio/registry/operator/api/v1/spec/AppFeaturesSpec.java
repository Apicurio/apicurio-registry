package io.apicurio.registry.operator.api.v1.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
@JsonPropertyOrder({ "allowDeletes" })
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class AppFeaturesSpec {

    @JsonProperty("allowDeletes")
    @JsonPropertyDescription("""
            Apicurio Registry backend 'allow deletes' feature.
            If the value is true, the application will be configured to allow Groups, Artifacts, and
            Artifact Versions to be deleted.  By default, resources in Registry are immutable and so
            cannot be deleted. Registry can be configured to allow deleting of these resources at a
            granular level (e.g. only allow deleting artifact versions) using ENV variables.  This
            option enables deletes for all three resource types.""")
    @JsonSetter(nulls = Nulls.SKIP)
    private Boolean allowDeletes;

}
