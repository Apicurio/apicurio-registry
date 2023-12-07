package io.apicurio.registry.support;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Citizen {

    @JsonProperty("firstName")
    private String firstName;

    @JsonProperty("lastName")
    private String lastName;

    @JsonProperty("age")
    private int age;

    @JsonProperty("city")
    City city;

    @JsonProperty("identifier")
    CitizenIdentifier identifier;

    @JsonProperty("qualifications")
    List<Qualification> qualifications;

    public Citizen() {
    }

    public Citizen(String firstName, String lastName, int age, City city, CitizenIdentifier identifier, List<Qualification> qualifications) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.city = city;
        this.qualifications = qualifications;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public City getCity() {
        return city;
    }

    public void setCity(City city) {
        this.city = city;
    }

    public CitizenIdentifier getIdentifier() {
        return identifier;
    }

    public void setIdentifier(CitizenIdentifier identifier) {
        this.identifier = identifier;
    }

    public List<Qualification> getQualifications() {
        return qualifications;
    }

    public void setQualifications(List<Qualification> qualifications) {
        this.qualifications = qualifications;
    }
}
