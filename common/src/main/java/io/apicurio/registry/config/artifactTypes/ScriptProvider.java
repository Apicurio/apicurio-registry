
package io.apicurio.registry.config.artifactTypes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "type",
    "scriptType",
    "script"
})
@io.quarkus.runtime.annotations.RegisterForReflection
public class ScriptProvider extends Provider
{
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("scriptType")
    private String scriptType;
    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("script")
    private String script;

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("scriptType")
    public String getScriptType() {
        return scriptType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("scriptType")
    public void setScriptType(String scriptType) {
        this.scriptType = scriptType;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("script")
    public String getScript() {
        return script;
    }

    /**
     * 
     * (Required)
     * 
     */
    @JsonProperty("script")
    public void setScript(String script) {
        this.script = script;
    }

}
