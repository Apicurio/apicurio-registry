package io.apicurio.registry.operator.state.impl;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@ApplicationScoped
@Getter
@Setter
public class ClusterInfo {

    private Optional<Boolean> isOCP;

    private Optional<String> canonicalHost;
}
