package io.apicurio.registry.rest;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.common.apps.config.Info;

import javax.inject.Singleton;

@Singleton
public class RestConfig {

    @ConfigProperty(name = "registry.rest.artifact.download.maxSize", defaultValue = "1000000")
    @Info(category = "rest", description = "Max size of the artifact allowed to be downloaded from URL", availableSince = "2.2.6-SNAPSHOT")
    int downloadMaxSize;

    @ConfigProperty(name = "registry.rest.artifact.download.skipSSLValidation", defaultValue = "false")
    @Info(category = "rest", description = "Skip SSL validation when downloading artifacts from URL", availableSince = "2.2.6-SNAPSHOT")
    boolean downloadSkipSSLValidation;

    public int getDownloadMaxSize() { return this.downloadMaxSize; }

    public boolean getDownloadSkipSSLValidation() { return this.downloadSkipSSLValidation; }

}
