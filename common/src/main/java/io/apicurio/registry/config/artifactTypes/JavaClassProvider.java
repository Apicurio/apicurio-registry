
package io.apicurio.registry.config.artifactTypes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
        "type",
        "classname"
})
@io.quarkus.runtime.annotations.RegisterForReflection
public class JavaClassProvider extends Provider {

    @JsonProperty("classname")
    private String classname;


    @JsonProperty("classname")
    public String getClassname() {
        return classname;
    }

    @JsonProperty("classname")
    public void setClassname(String classname) {
        this.classname = classname;
    }

}
