package io.apicurio.registry.utils.export;

public class SubjectVersionPair {

    private String subject;
    private int version;

    public SubjectVersionPair(String subject, int version) {
        this.subject = subject;
        this.version = version;
    }

    public String getSubject() {
        return subject;
    }

    public int getVersion() {
        return version;
    }

    public boolean is(String subject, int version) {
        return getSubject().equals(subject) && getVersion() == version;
    }
}
