package io.apicurio.registry.events.http;

import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.common.apps.config.Info;
import io.apicurio.registry.utils.RegistryProperties;

@ApplicationScoped
public class HttpEventSinkConfiguration {

    @ConfigProperty(name = "apicurio.events.ksink")
    @Info(category = "events", description = "Events Kafka sink enabled", availableSince = "2.0.0.Final")
    Optional<String> ksink;

    @Produces
    public HttpSinksConfiguration sinkConfig(@RegistryProperties(value = {"apicurio.events.sink"}) Properties properties) {
        List<HttpSinkConfiguration> httpSinks = properties.stringPropertyNames().stream()
            .map(key -> new HttpSinkConfiguration(key, properties.getProperty(key)))
            .collect(Collectors.toList());
        if (ksink.isPresent()) {
            httpSinks.add(new HttpSinkConfiguration("k_sink", ksink.get()));
        }
        return new HttpSinksConfiguration(httpSinks);
    }


}
