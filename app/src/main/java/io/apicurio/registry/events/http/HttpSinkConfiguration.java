package io.apicurio.registry.events.http;

public class HttpSinkConfiguration {
    
    private String name;
    private String endpoint;

    public HttpSinkConfiguration(String name, String endpoint) {
        this.name = name;
        this.endpoint = endpoint;
    }

    public String getName() {
        return name;
    }

    public String getEndpoint() {
        return endpoint;
    }

}