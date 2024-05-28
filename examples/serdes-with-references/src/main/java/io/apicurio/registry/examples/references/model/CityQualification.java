package io.apicurio.registry.examples.references.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CityQualification {

    @JsonProperty("subject_name")
    private String subjectName;

    @JsonProperty("qualification")
    private int qualification;

    public CityQualification() {
    }

    public CityQualification(String subjectName, int qualification) {
        this.subjectName = subjectName;
        this.qualification = qualification;
    }

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }

    public int getQualification() {
        return qualification;
    }

    public void setQualification(int qualification) {
        this.qualification = qualification;
    }

    @Override
    public String toString() {
        return "CityQualification{" +
                "subjectName='" + subjectName + '\'' +
                ", qualification=" + qualification +
                '}';
    }
}
