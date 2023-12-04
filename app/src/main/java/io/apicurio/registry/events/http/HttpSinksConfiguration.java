package io.apicurio.registry.events.http;

import java.util.List;


public class HttpSinksConfiguration {

    private List<HttpSinkConfiguration> httpSinks;

    public HttpSinksConfiguration(List<HttpSinkConfiguration> httpSinks) {
        this.httpSinks = httpSinks;
    }

    public List<HttpSinkConfiguration> httpSinks() {
        return this.httpSinks;
    }

    public boolean isConfigured() {
        return this.httpSinks != null && !this.httpSinks.isEmpty();
    }

}
