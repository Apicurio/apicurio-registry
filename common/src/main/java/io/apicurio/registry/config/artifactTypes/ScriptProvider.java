
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
    @JsonProperty("scriptType")
    private String scriptType;

    @JsonProperty("script")
    private String script;


    @JsonProperty("scriptType")
    public String getScriptType() {
        return scriptType;
    }

    @JsonProperty("scriptType")
    public void setScriptType(String scriptType) {
        this.scriptType = scriptType;
    }

    @JsonProperty("script")
    public String getScript() {
        return script;
    }

    @JsonProperty("script")
    public void setScript(String script) {
        this.script = script;
    }

}
