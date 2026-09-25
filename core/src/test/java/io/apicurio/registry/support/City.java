package io.apicurio.registry.support;

import com.fasterxml.jackson.annotation.JsonProperty;

public class City {

    @JsonProperty("name")
    private String name;

    @JsonProperty("zipCode")
    private Integer zipCode;

    @JsonProperty("qualification")
    private CityQualification qualification;

    public City() {
    }

    public City(String name, Integer zipCode) {
        this.name = name;
        this.zipCode = zipCode;
    }

    public City(String name, Integer zipCode, CityQualification cityQualification) {
        this.name = name;
        this.zipCode = zipCode;
        this.qualification = cityQualification;
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

    public CityQualification getQualification() {
        return qualification;
    }

    public void setQualification(CityQualification qualification) {
        this.qualification = qualification;
    }
}
