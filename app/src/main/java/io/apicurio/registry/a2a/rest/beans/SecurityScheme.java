package io.apicurio.registry.a2a.rest.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a security scheme for an A2A agent, modeled as a flat discriminated union.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecurityScheme {

    @JsonProperty("type")
    private String type;

    @JsonProperty("description")
    private String description;

    @JsonProperty("location")
    private String location;

    @JsonProperty("name")
    private String name;

    @JsonProperty("scheme")
    private String scheme;

    @JsonProperty("bearerFormat")
    private String bearerFormat;

    @JsonProperty("flows")
    private Map<String, Object> flows;

    @JsonProperty("oauth2MetadataUrl")
    private String oauth2MetadataUrl;

    @JsonProperty("openIdConnectUrl")
    private String openIdConnectUrl;

    public SecurityScheme() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getBearerFormat() {
        return bearerFormat;
    }

    public void setBearerFormat(String bearerFormat) {
        this.bearerFormat = bearerFormat;
    }

    public Map<String, Object> getFlows() {
        return flows;
    }

    public void setFlows(Map<String, Object> flows) {
        this.flows = flows;
    }

    public String getOauth2MetadataUrl() {
        return oauth2MetadataUrl;
    }

    public void setOauth2MetadataUrl(String oauth2MetadataUrl) {
        this.oauth2MetadataUrl = oauth2MetadataUrl;
    }

    public String getOpenIdConnectUrl() {
        return openIdConnectUrl;
    }

    public void setOpenIdConnectUrl(String openIdConnectUrl) {
        this.openIdConnectUrl = openIdConnectUrl;
    }

    public static SecurityScheme httpAuth(String scheme) {
        SecurityScheme s = new SecurityScheme();
        s.setType("httpAuth");
        s.setScheme(scheme);
        return s;
    }

    public static SecurityScheme openIdConnect(String url) {
        SecurityScheme s = new SecurityScheme();
        s.setType("openIdConnect");
        s.setOpenIdConnectUrl(url);
        return s;
    }

    public static SecurityScheme apiKey(String name, String location) {
        SecurityScheme s = new SecurityScheme();
        s.setType("apiKey");
        s.setName(name);
        s.setLocation(location);
        return s;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SecurityScheme securityScheme = new SecurityScheme();

        public Builder type(String type) {
            securityScheme.setType(type);
            return this;
        }

        public Builder description(String description) {
            securityScheme.setDescription(description);
            return this;
        }

        public Builder location(String location) {
            securityScheme.setLocation(location);
            return this;
        }

        public Builder name(String name) {
            securityScheme.setName(name);
            return this;
        }

        public Builder scheme(String scheme) {
            securityScheme.setScheme(scheme);
            return this;
        }

        public Builder bearerFormat(String bearerFormat) {
            securityScheme.setBearerFormat(bearerFormat);
            return this;
        }

        public Builder flows(Map<String, Object> flows) {
            securityScheme.setFlows(flows);
            return this;
        }

        public Builder oauth2MetadataUrl(String oauth2MetadataUrl) {
            securityScheme.setOauth2MetadataUrl(oauth2MetadataUrl);
            return this;
        }

        public Builder openIdConnectUrl(String openIdConnectUrl) {
            securityScheme.setOpenIdConnectUrl(openIdConnectUrl);
            return this;
        }

        public SecurityScheme build() {
            return securityScheme;
        }
    }
}
