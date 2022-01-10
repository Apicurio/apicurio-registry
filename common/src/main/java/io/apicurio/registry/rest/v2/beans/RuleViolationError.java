
package io.apicurio.registry.rest.v2.beans;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Root Type for Error
 * <p>
 * All error responses, whether `4xx` or `5xx` will include one of these as the response
 * body.
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "causes"
})
@Generated("jsonschema2pojo")
@io.quarkus.runtime.annotations.RegisterForReflection
@lombok.Builder()
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.ToString
public class RuleViolationError
    extends Error
{

    /**
     * List of rule violation causes.
     * (Required)
     *
     */
    @JsonProperty("causes")
    @JsonPropertyDescription("List of rule violation causes.")
    private List<RuleViolationCause> causes = new ArrayList<RuleViolationCause>();


    /**
     * List of rule violation causes.
     * (Required)
     *
     */
    @JsonProperty("causes")
    public List<RuleViolationCause> getCauses() {
        return causes;
    }

    /**
     * List of rule violation causes.
     * (Required)
     *
     */
    @JsonProperty("causes")
    public void setCauses(List<RuleViolationCause> causes) {
        this.causes = causes;
    }

}
