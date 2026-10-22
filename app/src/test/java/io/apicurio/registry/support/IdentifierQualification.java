package io.apicurio.registry.support;

import com.fasterxml.jackson.annotation.JsonProperty;

public class IdentifierQualification {

    @JsonProperty("subject_name")
    private String subjectName;

    @JsonProperty("qualification")
    private int qualification;

    public IdentifierQualification() {
    }

    public IdentifierQualification(String subjectName, int qualification) {
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
}
