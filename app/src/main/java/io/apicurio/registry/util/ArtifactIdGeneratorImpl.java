package io.apicurio.registry.util;

import java.util.UUID;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ArtifactIdGeneratorImpl implements ArtifactIdGenerator {

    public String generate() {
        return UUID.randomUUID().toString();
    }
    
}
