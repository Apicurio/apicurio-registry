
package io.apicurio.registry.config.artifactTypes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type",
    "scriptLocation"
})
@io.quarkus.runtime.annotations.RegisterForReflection
public class ScriptProvider extends Provider
{

    @JsonProperty("scriptLocation")
    private String scriptLocation;

    @JsonProperty("scriptLocation")
    public String getScriptLocation() {
        return scriptLocation;
    }

    @JsonProperty("scriptLocation")
    public void setScriptLocation(String scriptLocation) {
        this.scriptLocation = scriptLocation;
    }

}
