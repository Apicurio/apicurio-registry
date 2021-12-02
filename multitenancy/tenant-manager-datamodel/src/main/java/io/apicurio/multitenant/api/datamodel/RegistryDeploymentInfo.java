
package io.apicurio.multitenant.api.datamodel;

import javax.annotation.processing.Generated;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * Root Type for RegistryDeployment
 * <p>
 * Basic information of the Registry deployment that the Tenant Manager API is managing
 * 
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "url"
})
@Generated("jsonschema2pojo")
public class RegistryDeploymentInfo {

    @JsonProperty("name")
    private String name;
    @JsonProperty("url")
    private String url;

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    @JsonProperty("url")
    public void setUrl(String url) {
        this.url = url;
    }

}
