package io.apicurio.registry.support;

import com.fasterxml.jackson.annotation.JsonProperty;

public class City {

    @JsonProperty("name")
    private String name;

    @JsonProperty("zipCode")
    private Integer zipCode;

    public City() {
    }

    public City(String name, Integer zipCode) {
        this.name = name;
        this.zipCode = zipCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getZipCode() {
        return zipCode;
    }

    public void setZipCode(Integer zipCode) {
        this.zipCode = zipCode;
    }
}
