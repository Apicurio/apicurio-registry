package io.apicurio.registry.util;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class ArtifactIdGeneratorImpl implements ArtifactIdGenerator {

    public String generate() {
        return UUID.randomUUID().toString();
    }

}
