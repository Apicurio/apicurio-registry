package io.apicurio.registry.rest;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Singleton;

@Singleton
public class RestConfig {

    @ConfigProperty(name = "registry.rest.artifact.download.maxSize", defaultValue = "1000000")
    int downloadMaxSize;

    @ConfigProperty(name = "registry.rest.artifact.download.skipSSLValidation", defaultValue = "false")
    boolean downloadSkipSSLValidation;

    public int getDownloadMaxSize() { return this.downloadMaxSize; }

    public boolean getDownloadSkipSSLValidation() { return this.downloadSkipSSLValidation; }

}
