package io.apicurio.registry.rest;

import jakarta.enterprise.context.RequestScoped;
import lombok.Getter;
import lombok.Setter;

@RequestScoped
public class CreatedResourceContext {

    @Getter
    @Setter
    private String createdId;
}
